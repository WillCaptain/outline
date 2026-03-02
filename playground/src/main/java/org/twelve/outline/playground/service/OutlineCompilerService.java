package org.twelve.outline.playground.service;

import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.ast.Node;
import org.twelve.gcp.exception.GCPError;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.gcp.node.expression.OutlineDefinition;
import org.twelve.gcp.node.statement.OutlineDeclarator;
import org.twelve.gcp.node.statement.VariableDeclarator;
import org.twelve.gcp.node.expression.Assignment;
import org.twelve.gcp.outline.Outline;
import org.twelve.gcp.outline.builtin.ERROR;
import org.twelve.gcp.outline.builtin.IGNORE;
import org.twelve.gcp.outline.builtin.UNKNOWN;
import org.twelve.msll.exception.AggregateGrammarSyntaxException;
import org.twelve.msll.exception.GrammarSyntaxException;
import org.twelve.outline.OutlineParser;
import org.twelve.outline.playground.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OutlineCompilerService {

    // Reuse the shared grammar tables across requests (thread-safe)
    private static final OutlineParser PARSER = new OutlineParser();

    private final ExamplesRepository examplesRepository;

    public OutlineCompilerService(ExamplesRepository examplesRepository) {
        this.examplesRepository = examplesRepository;
    }

    // ─── Main compilation entry point ─────────────────────────────────────────

    public CompileResponse compile(String code) {
        List<DiagnosticInfo> diagnostics = new ArrayList<>();
        List<SymbolInfo> symbols = new ArrayList<>();
        String output = null;
        List<ConsoleEntry> consoleLogs = new ArrayList<>();
        boolean hasParseError = false;
        long inferenceMs = 0;
        long executionMs = 0;

        try {
            // ── 1. Parse ────────────────────────────────────────────────────
            ASF asf = new ASF();
            AST ast = PARSER.parse(asf, code);

            // ── 2. Type inference ───────────────────────────────────────────
            long t0 = System.currentTimeMillis();
            try {
                asf.infer();
            } catch (Exception e) {
                // inference exceptions end up in ast.errors() – nothing extra needed
            }
            inferenceMs = System.currentTimeMillis() - t0;

            // Collect inference diagnostics — downgrade internal SYSTEM errors to warnings
            for (GCPError err : asf.allErrors()) {
                int line = (err.node() != null) ? err.node().loc().line() : -1;
                int col  = (err.node() != null) ? err.node().loc().col()  : -1;
                boolean isSystem = err.errorCode() != null &&
                        err.errorCode().getCategory() == org.twelve.gcp.exception.GCPErrCode.ErrorCategory.SYSTEM;
                diagnostics.add(new DiagnosticInfo(err.toString(), line, col,
                        isSystem ? "warning" : "error"));
            }

            // ── 3. Extract symbol types ─────────────────────────────────────
            symbols = extractSymbols(ast);

            // ── 4. Execute (capture print / Console.log / .warn / .error) ───
            long t1 = System.currentTimeMillis();
            org.twelve.gcp.interpreter.stdlib.ConsoleCapture.start();
            try {
                Value result = asf.interpret();
                output = formatValue(result);
            } catch (Exception e) {
                output = "⚠ Runtime error: " + e.getMessage();
            } finally {
                consoleLogs = org.twelve.gcp.interpreter.stdlib.ConsoleCapture.collect()
                        .stream()
                        .map(e -> new ConsoleEntry(e.level().name().toLowerCase(), e.message()))
                        .collect(Collectors.toList());
            }
            executionMs = System.currentTimeMillis() - t1;

        } catch (AggregateGrammarSyntaxException agg) {
            hasParseError = true;
            for (var err : agg.errors()) {
                diagnostics.add(DiagnosticInfo.error(trimParseError(err.getMessage())));
            }
        } catch (GrammarSyntaxException gse) {
            hasParseError = true;
            diagnostics.add(DiagnosticInfo.error(trimParseError(gse.getMessage())));
        } catch (Exception ex) {
            diagnostics.add(DiagnosticInfo.error("Internal error: " + ex.getMessage()));
        }

        return new CompileResponse(symbols, diagnostics, output, consoleLogs,
                hasParseError, inferenceMs, executionMs);
    }

    // ─── Symbol extraction (walk the AST for variable/outline declarators) ────

    private List<SymbolInfo> extractSymbols(AST ast) {
        List<SymbolInfo> result = new ArrayList<>();
        walkForSymbols(ast.program(), result);
        return result;
    }

    private void walkForSymbols(Node node, List<SymbolInfo> out) {
        if (node instanceof VariableDeclarator vd) {
            for (Assignment a : vd.assignments()) {
                Node lhs = a.lhs();
                if (lhs == null) continue;
                Outline outline = lhs.outline();
                if (outline == null || outline instanceof UNKNOWN || outline instanceof ERROR) continue;
                String typeStr = formatType(outline.toString());
                out.add(new SymbolInfo(
                        lhs.lexeme(),
                        typeStr,
                        vd.kind().name().toLowerCase(),
                        lhs.loc().line(),
                        lhs.loc().col()
                ));
            }
        } else if (node instanceof OutlineDeclarator od) {
            // Each outline statement may define multiple types (outline A = ..., B = ...)
            for (OutlineDefinition def : od.definitions()) {
                var sym = def.symbolNode();
                if (sym == null) continue;
                Outline outline = sym.outline();
                if (outline == null || outline instanceof UNKNOWN || outline instanceof ERROR) continue;
                out.add(new SymbolInfo(
                        sym.lexeme(),
                        formatType(outline.toString()),
                        "outline",
                        sym.loc().line(),
                        sym.loc().col()
                ));
            }
        }
        for (Node child : node.nodes()) {
            walkForSymbols(child, out);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Converts GCP's internal type representation to a clean, user-facing display string.
     * E.g. "Int->Int->Int" → "Int → Int → Int", "INTEGER" → "Int"
     */
    private String formatType(String raw) {
        if (raw == null) return "?";
        return raw
                .replace("->", " → ")
                // GCP internal class names → Outline language type names
                .replace("INTEGER",  "Int")
                .replace("LONG",     "Long")
                .replace("FLOAT",    "Float")
                .replace("DOUBLE",   "Double")
                .replace("STRING",   "String")
                .replace("BOOL",     "Bool")
                .replace("NUMBER",   "Number")
                .replace("UNIT",     "Unit")
                .replace("UNKNOWN",  "?")
                .replace("IGNORE",   "()")
                // Java toString artefacts
                .replace("Integer",  "Int")
                // GCP generic/internal placeholder names
                .replace("any",      "α")
                .replace("null",     "β")
                .replace("`", "")
                .trim();
    }

    private String formatValue(Value v) {
        if (v == null) return "()";
        String s = v.toString();
        // Strip synthetic class-name suffixes like "UnitValue@1a2b3c"
        if (s.contains("@")) return "()";
        // Strip unnecessary outer quotes that some Value impls add
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    // ─── Parse-error helper ───────────────────────────────────────────────────

    /**
     * Strips the verbose MSLL token-dump noise from a raw parse-error message,
     * keeping only the human-readable portion that the developer actually needs.
     */
    private String trimParseError(String msg) {
        if (msg == null) return "Parse error";
        // Remove the "the possible productions should be matched would be: ..." tail
        int cut = msg.indexOf("the possible productions should be matched");
        if (cut > 0) msg = msg.substring(0, cut).stripTrailing();
        // Collapse excess blank lines
        return msg.replaceAll("\n{3,}", "\n\n").trim();
    }

    // ─── Example library (delegated to ExamplesRepository) ───────────────────

    public List<ExampleCode> examples() {
        return examplesRepository.findAll();
    }

    public ExampleCode addExample(ExampleCode ex) {
        return examplesRepository.save(ex);
    }

    // ─── (Legacy hardcoded examples – kept as fallback reference) ─────────────
    @SuppressWarnings("unused")
    private List<ExampleCode> _legacyExamples() {
        return List.of(

            // ── Basics ─────────────────────────────────────────────────────────
            new ExampleCode("hello", "Hello, GCP",
                "Every type inferred from zero annotations – welcome to GCP.",
                "basics",
                """
                // GCP infers types for every binding — no annotations needed
                let x    = 42;          // → Int
                let name = "Outline";   // → String
                let pi   = 3.14159;     // → Float
                let flag = true;        // → Bool
                let msg  = "Hello, " + name + "!";
                msg;
                """),

            new ExampleCode("string-ops", "String & Escape Sequences",
                "String concatenation, escape sequences (\\n \\t \\\") — all decoded at parse time.",
                "basics",
                """
                let greet = name -> "Hello, " + name + "!";

                // Escape sequences decoded at parse time
                let newline = "line1\\nline2";
                let tab     = "col1\\tcol2";
                let quote   = "She said \\"hi\\"";

                let msg1 = greet("World");
                let msg2 = greet("GCP");
                msg1;
                """),

            new ExampleCode("arithmetic", "Numeric Tower",
                "GCP infers the narrowest numeric type automatically.",
                "basics",
                """
                let a = 10;
                let b = 3;

                let sum   = a + b;     // → Int
                let diff  = a - b;     // → Int
                let prod  = a * b;     // → Int
                let quot  = a / b;     // → Int
                let mod   = a % b;     // → Int
                let power = a * a;     // → Int

                // Mixed types widen automatically
                let x = 1;
                let y = 2.5;
                let z = x + y;        // → Float | Double

                power;
                """),

            // ── Functions ──────────────────────────────────────────────────────
            new ExampleCode("curried", "Curried Functions",
                "All Outline functions are curried — GCP infers the full A→B→C chain.",
                "functions",
                """
                // Curried binary functions
                let add = x -> y -> x + y;   // Int → Int → Int
                let mul = x -> y -> x * y;   // Int → Int → Int

                // Partial application creates specialised functions
                let add5   = add(5);          // Int → Int
                let double = mul(2);          // Int → Int (or Number → Number)

                // Compose via application
                let result = add5(double(3)); // = add5(6) = 11

                result;
                """),

            new ExampleCode("hof", "Higher-Order Functions",
                "Functions that accept or return functions — GCP tracks the full type chain.",
                "functions",
                """
                // Classic higher-order combinators
                let apply   = f -> x -> f(x);
                let compose = f -> g -> x -> f(g(x));
                let twice   = f -> x -> f(f(x));
                let flip    = f -> x -> y -> f(y)(x);

                let inc    = x -> x + 1;
                let double = x -> x * 2;

                // Compose inc then double: (5+1)*2 = 12
                let inc_then_double = compose(double)(inc);

                // Apply a function four times: 3 * 2 * 2 = 12
                let four_times = twice(double);

                // flip argument order: sub(3)(10) = 10 - 3 = 7
                let sub  = x -> y -> x - y;
                let flipped_sub = flip(sub);

                let r1 = apply(inc)(10);          // 11
                let r2 = inc_then_double(5);      // 12
                let r3 = four_times(3);           // 12
                let r4 = flipped_sub(3)(10);      // 7
                r2;
                """),

            new ExampleCode("recursive", "Recursive Functions",
                "GCP handles mutual recursion and infers fixed-point types.",
                "functions",
                """
                // Classic math functions
                let fact = n -> if (n <= 1) 1 else n * fact(n - 1);
                let fib  = n -> if (n <= 1) n else fib(n - 1) + fib(n - 2);

                // GCD via Euclidean algorithm (curried)
                let gcd = x -> y -> if (y == 0) x else gcd(y)(x % y);
                let lcm = x -> y -> (x * y) / (gcd(x)(y));

                let f5  = fact(5);           // 120
                let f10 = fib(10);           // 55
                let g   = gcd(48)(18);       // 6
                let l   = lcm(4)(6);         // 12

                f5;
                """),

            // ── Types ──────────────────────────────────────────────────────────
            new ExampleCode("adt-sum", "Sum Types (ADT)",
                "GCP infers the precise ADT variant for every match branch.",
                "types",
                """
                // Define algebraic sum types
                outline Color     = Red | Green | Blue;
                outline Direction = North | South | East | West;

                let c = Green;
                let d = North;

                // Pattern match — GCP infers result type String
                let color_name = match c {
                    Red   -> "red",
                    Green -> "green",
                    Blue  -> "blue"
                };

                let is_vertical = match d {
                    North -> true,
                    South -> true,
                    _     -> false
                };

                color_name;
                """),

            new ExampleCode("adt-entity", "Entity Types (Product Types)",
                "Struct-like entity types with typed fields — GCP infers field access types.",
                "types",
                """
                // Product types (records / structs)
                outline Point  = {x:Int, y:Int};
                outline Person = {name:String, age:Int};

                let origin = Point{x=0, y=0};
                let p      = Point{x=3, y=4};
                let alice  = Person{name="Alice", age=30};

                // Field access — GCP narrows type to Int
                let dist_sq = a -> b -> a * a + b * b;
                let d = dist_sq(p.x)(p.y);   // 3² + 4² = 25

                d;
                """),

            new ExampleCode("nested-entity", "Nested Entities",
                "Deep-nested anonymous entity literals — GCP resolves each field accessor chain.",
                "types",
                """
                let father = {
                    name = "will",
                    son = {name="evan", girl_friend={name="someone"},gender="male"}
                };

                // Chained member access across three levels of nesting
                let gf_name = father.son.girl_friend.name;   // "someone"
                let son_name = father.son.name;               // "evan"
                let dad_name = father.name;                   // "will"

                gf_name;
                """),

            new ExampleCode("match-adt", "Pattern Matching + Guards",
                "Exhaustive match with guard conditions — GCP infers a unified result type.",
                "types",
                """
                outline Shape = Circle{r:Int} | Rect{w:Int, h:Int} | Dot;

                // Dispatch on variant, unpack fields inline
                let area = s -> match s {
                    Circle{r}    -> r * r,
                    Rect{w, h}   -> w * h,
                    Dot          -> 0
                };

                let describe = s -> match s {
                    Circle{r} if r > 10 -> "big circle",
                    Circle{r}           -> "small circle",
                    Rect{w, h}          -> "rectangle",
                    Dot                 -> "dot"
                };

                let c = Circle{r=7};
                let r = Rect{w=4, h=5};
                area(r);
                """),

            // ── Collections ────────────────────────────────────────────────────
            new ExampleCode("arrays", "Arrays & Pipelines",
                "Ranges with variables, map/filter/reduce — all inferred end-to-end.",
                "collections",
                """
                let nums  = [1, 2, 3, 4, 5];
                let n     = 10;

                // Dynamic range: [1..n] — n is a variable!
                let range = [1...n];

                // Pipeline: filter → map
                let evens = [1...n].filter(x -> x % 2 == 0);   // [2,4,6,8,10]
                let sq    = [1...5].map(x -> x * x);            // [1,4,9,16,25]

                // Gauss sum: n*(n+1)/2 = 5050
                let total = [1...100].reduce(0, (acc, x) -> acc + x);

                total;
                """),

            // ── Advanced ───────────────────────────────────────────────────────
            new ExampleCode("inference-power", "⚡ GCP Inference Showcase",
                "Generics, HOFs, ADTs and closures — GCP infers it all with zero annotations.",
                "advanced",
                """
                // ── Generic identity: inferred as ∀α. α → α
                let id = x -> x;

                // ── Church numerals — numbers as functions!
                let zero  = f -> x -> x;
                let succ  = n -> f -> x -> f(n(f)(x));
                let one   = succ(zero);
                let two   = succ(one);
                let three = succ(two);

                // ── to_int: decode a Church numeral
                let to_int = n -> n(x -> x + 1)(0);

                // ── Church addition: add(m)(n) = m + n
                let church_add = m -> n -> f -> x -> m(f)(n(f)(x));
                let five = church_add(two)(three);

                to_int(five);   // → 5
                """),

            new ExampleCode("gcp-generics", "Generic Functions",
                "GCP propagates generic type variables through arbitrary call chains.",
                "advanced",
                """
                // Swap a pair — inferred as (A, B) → (B, A)
                let swap = p -> (p.second, p.first);

                // Const: ignore second argument — A → B → A
                let konst = a -> b -> a;

                // SKI combinators
                let s = f -> g -> x -> f(x)(g(x));
                let k = a -> b -> a;
                let i = s(k)(k);   // identity via SKI

                // Fix-point (Y combinator approximation)
                let fix = f -> (x -> f(x -> f(x)(x)))(x -> f(x -> f(x)(x)));

                let fact_gen = self -> n -> if (n <= 1) 1 else n * self(n - 1);
                let fact = fix(fact_gen);

                fact(6);   // → 720
                """),

            new ExampleCode("pipeline", "Data Pipeline",
                "A realistic data-processing pipeline — GCP tracks types through every stage.",
                "advanced",
                """
                // Simulate a small data pipeline
                let data = [3, 1, 4, 1, 5, 9, 2, 6, 5, 3];

                // Step 1: deduplicate (keep first occurrence)
                let unique = arr -> arr.filter((x, i) -> i == arr.indexOf(x));

                // Step 2: sort ascending (bubble-sort via reduce — simple demo)
                let min = a -> b -> if (a < b) a else b;

                // Step 3: filter > threshold
                let threshold = 3;
                let hi = data.filter(x -> x > threshold);

                // Step 4: double each
                let doubled = hi.map(x -> x * 2);

                // Step 5: sum
                let result = doubled.reduce(0, (acc, x) -> acc + x);

                result;
                """)
        );
    }
}

