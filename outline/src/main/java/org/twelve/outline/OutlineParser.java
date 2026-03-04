package org.twelve.outline;

import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.msll.grammar.Grammars;
import org.twelve.msll.parser.MyParser;
import org.twelve.msll.parserbuilder.MyParserBuilder;
import org.twelve.msll.parsetree.LexerRuleTree;
import org.twelve.msll.parsetree.ParserGrammarTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Facade for parsing Outline source code into GCP ASTs.
 *
 * <h2>Performance design – shared grammar tables</h2>
 * Building the LL(*) predict table from the grammar files is expensive and deterministic.
 * The underlying {@link MyParserBuilder} (which holds {@link Grammars}, the predict table,
 * {@link ParserGrammarTree} and {@link LexerRuleTree}) is therefore cached as a
 * <em>static singleton</em> and reused by every {@code OutlineParser} instance.
 * Grammar compilation now happens <strong>once per JVM</strong> rather than once per parser
 * instantiation, giving a large speed-up for any code path that creates multiple parsers
 * (e.g. multi-module import/export tests).
 *
 * <h2>State-isolation design – two modes</h2>
 * <ul>
 *   <li><b>Isolated mode</b> ({@code new OutlineParser()}) – {@link #parse(String)} creates
 *       a <em>fresh</em> {@link ASF} for each call so that single-module parses are completely
 *       independent.  This is the default and prevents test cross-contamination.</li>
 *   <li><b>Shared-ASF mode</b> ({@code new OutlineParser(GCPConverter)}) – {@link #parse(String)}
 *       feeds through the converter supplied at construction time, accumulating all parsed modules
 *       in the same {@link ASF}.  Use this mode for multi-module scenarios where cross-module
 *       imports must be resolved.</li>
 * </ul>
 */
public class OutlineParser {

    private static final String PARSER_GRAMMAR = "outlineParser.gm";
    private static final String LEXER_GRAMMAR  = "outlineLexer.gm";

    // ── static singleton grammar tables ───────────────────────────────────────

    private static volatile MyParserBuilder SHARED_BUILDER;

    private static MyParserBuilder sharedBuilder() {
        if (SHARED_BUILDER == null) {
            synchronized (OutlineParser.class) {
                if (SHARED_BUILDER == null) {
                    try {
                        // Use getResourceAsStream so grammar files can be loaded both from
                        // the filesystem (during development) and from inside an executable JAR.
                        ClassLoader cl = OutlineParser.class.getClassLoader();
                        InputStream parserStream = cl.getResourceAsStream(PARSER_GRAMMAR);
                        InputStream lexerStream  = cl.getResourceAsStream(LEXER_GRAMMAR);
                        if (parserStream == null || lexerStream == null) {
                            // Fall back to file-path loading (unit-test / IDE classpath)
                            SHARED_BUILDER = new MyParserBuilder(PARSER_GRAMMAR, LEXER_GRAMMAR);
                        } else {
                            SHARED_BUILDER = new MyParserBuilder(
                                    new InputStreamReader(parserStream),
                                    new InputStreamReader(lexerStream));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialise Outline grammar", e);
                    }
                }
            }
        }
        return SHARED_BUILDER;
    }

    // ── public grammar accessors kept for existing callers ────────────────────

    public ParserGrammarTree parserGrammarTree() { return sharedBuilder().parserGrammarTree(); }
    public LexerRuleTree     lexerGrammarTree()  { return sharedBuilder().lexerGrammarTree(); }
    public Grammars          grammars()          { return sharedBuilder().grammars(); }

    // ── instance state ────────────────────────────────────────────────────────

    /**
     * Non-null only in shared-ASF (multi-module) mode.
     * When non-null, {@link #parse(String)} routes through this converter so that all
     * modules are accumulated in the same {@link ASF}.
     */
    private final GCPConverter sharedConverter;

    // ── constructors ──────────────────────────────────────────────────────────

    /**
     * <b>Isolated mode</b>: every call to {@link #parse(String)} creates a fresh {@link ASF}.
     * Use this for single-module tests and tooling.
     * <p>
     * Eagerly initialises the shared grammar tables so that the first {@link #parse} call
     * does not incur the one-time compilation cost (which would otherwise be charged to
     * whichever test or module happens to run first).
     */
    public OutlineParser() {
        this.sharedConverter = null;
        sharedBuilder(); // warm up: compile grammar tables now, not on first parse
    }

    /**
     * <b>Shared-ASF mode</b>: every call to {@link #parse(String)} feeds through
     * {@code converter}, accumulating all modules in the same {@link ASF}.
     * Use this when cross-module imports need to be resolved.
     *
     * <p>No checked exception is thrown; grammar initialisation happens in the static
     * initialiser and is only performed once.
     */
    public OutlineParser(GCPConverter converter) {
        this.sharedConverter = converter;
        sharedBuilder(); // warm up
    }

    // ── parse methods ─────────────────────────────────────────────────────────

    /**
     * Parses {@code code} according to the current mode:
     * <ul>
     *   <li>Isolated mode → fresh {@link ASF} each call (no shared state).</li>
     *   <li>Shared-ASF mode → routes through the constructor-supplied converter.</li>
     * </ul>
     */
    public AST parse(String code) {
        if (sharedConverter != null) {
            MyParser parser = sharedBuilder().createParser(code);
            AST ast = sharedConverter.convert(parser.parse());
            ast.setSourceCode(code);
            return ast;
        }
        return parse(new ASF(), code);
    }

    /**
     * Parses {@code code} and appends the resulting AST to {@code asf}.
     * Always uses an independent {@link GCPConverter} bound to the given {@code asf};
     * the constructor-supplied converter (if any) is not used.
     */
    public AST parse(ASF asf, String code) {
        MyParser parser = sharedBuilder().createParser(code);
        AST ast = new GCPConverter(asf).convert(parser.parse());
        ast.setSourceCode(code);
        return ast;
    }
}
