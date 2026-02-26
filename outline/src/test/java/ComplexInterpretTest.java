import org.junit.jupiter.api.Test;
import org.twelve.gcp.interpreter.value.TupleValue;
import org.twelve.gcp.interpreter.value.Value;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ComplexInterpretTest {
    // =========================================================================
    // 1. SUPER TEST 1 — Algorithm Benchmark (Rosetta Code style)
    //
    // Covers in one snippet:
    //   recursive math  : gcd, lcm, factorial, fibonacci
    //   merge sort      : recursive, take/drop/concat
    //   palindrome      : chars() / reverse() / reduce string rebuild
    //   HOF pipeline    : compose / partial application
    //   statistics      : sum / mean via built-in reduce
    //   closures        : partial application capture
    // =========================================================================

    @Test
    void test_super_algorithm_benchmark() {
        Value v = RunnerHelper.run("""
                let gcd  = a -> b -> if(b == 0){ a }else{ gcd(b)(a % b) };
                let lcm  = x -> y -> (x * y) / (gcd(x)(y));
                let fact = n -> if(n <= 1){ 1 }else{ n * fact(n-1) };
                let fib  = n -> if(n <= 1){ n }else{ fib(n-1) + fib(n-2) };
                let merge = a -> b -> {
                    if(a.len() == 0){ b }
                    else if(b.len() == 0){ a }
                    else{
                        let ha = a[0];
                        let hb = b[0];
                        let d  = ha - hb;
                        if(d <= 0){ [ha].concat(merge(a.drop(1))(b)) }
                        else{       [hb].concat(merge(a)(b.drop(1))) }
                    }
                };
                let msort = arr -> {
                    let n = arr.len();
                    if(n <= 1){ arr }
                    else{
                        let mid   = n / 2;
                        let left  = msort(arr.take(mid));
                        let right = msort(arr.drop(mid));
                        merge(left)(right)
                    }
                };
                let rev_str    = s -> s.chars().reverse().reduce(a -> b -> a + b)("");
                let palindrome = s -> s == rev_str(s);
                let compose    = f -> g -> x -> f(g(x));
                let add        = a -> b -> a + b;
                let mul        = a -> b -> a * b;
                let add5       = add(5);
                let dbl        = mul(2);
                let add5_then_dbl = compose(dbl)(add5);
                let sum_arr = arr -> arr.reduce(a -> b -> a + b)(0);
                let mean    = arr -> sum_arr(arr) / arr.len();
                let sorted  = msort([5, 3, 8, 1, 9, 2, 7, 4, 6]);
                let data    = [4, 7, 2, 9, 1, 5, 8, 3, 6];
                let primes  = [2, 3, 5, 7, 11, 13, 17, 19];
                (gcd(48)(18),
                 lcm(4)(6),
                 fact(7),
                 fib(10),
                 primes.len(),
                 sorted,
                 sorted[6],
                 palindrome("racecar"),
                 palindrome("hello").not(),
                 add5_then_dbl(10),
                 sum_arr(data),
                 mean(data))
                """);

        assertThat(v).isInstanceOf(TupleValue.class);
        TupleValue tv = (TupleValue) v;

        assertThat(RunnerHelper.intVal(tv.get(0))).isEqualTo(6L);         // gcd(48,18)
        assertThat(RunnerHelper.intVal(tv.get(1))).isEqualTo(12L);        // lcm(4,6)
        assertThat(RunnerHelper.intVal(tv.get(2))).isEqualTo(5040L);      // 7!
        assertThat(RunnerHelper.intVal(tv.get(3))).isEqualTo(55L);        // fib(10)
        assertThat(RunnerHelper.intVal(tv.get(4))).isEqualTo(8L);         // 8 primes

        List<Value> sorted = RunnerHelper.arrVal(tv.get(5));
        assertThat(sorted).hasSize(9);
        for (int i = 0; i < 9; i++) assertThat(RunnerHelper.intVal(sorted.get(i))).isEqualTo(i + 1L);

        assertThat(RunnerHelper.intVal(tv.get(6))).isEqualTo(7L);         // sorted[6] = 7
        assertThat(RunnerHelper.boolVal(tv.get(7))).isTrue();             // palindrome
        assertThat(RunnerHelper.boolVal(tv.get(8))).isTrue();             // not palindrome
        assertThat(RunnerHelper.intVal(tv.get(9))).isEqualTo(30L);        // compose HOF
        assertThat(RunnerHelper.intVal(tv.get(10))).isEqualTo(45L);       // sum
        assertThat(RunnerHelper.intVal(tv.get(11))).isEqualTo(5L);        // mean
    }

    // =========================================================================
    // 2. SUPER TEST 2 — Functional Data Analysis Pipeline
    //
    // Simulates a student grade processing system:
    //   entity construction    : Student records with multiple fields
    //   if-else grade classify : A/B/C/F via nested conditionals
    //   HOF pipeline           : filter / map / reduce / sort chained
    //   closure capture        : subj_avg captures enclosing rich array
    //   entity inheritance     : enriched student entity
    //   string formatting      : name.to_upper()
    // =========================================================================

    @Test
    void test_super_data_pipeline() {
        Value v = RunnerHelper.run("""
                let grade_of = score ->
                    if(score >= 90){ "A" }
                    else if(score >= 80){ "B" }
                    else if(score >= 70){ "C" }
                    else{ "F" };
                let students = [
                    {name = "Alice",   subject = "Math",    score = 95},
                    {name = "Bob",     subject = "Math",    score = 72},
                    {name = "Charlie", subject = "Science", score = 88},
                    {name = "Diana",   subject = "Science", score = 61},
                    {name = "Eve",     subject = "Math",    score = 83},
                    {name = "Frank",   subject = "Science", score = 91},
                    {name = "Grace",   subject = "Math",    score = 54}
                ];

                let enrich = s -> {
                    grade      = grade_of(s.score),
                    name       = s.name,
                    subject    = s.subject,
                    score      = s.score,
                    normalized = s.name.to_upper()
                };
                let rich = students.map(enrich);
                let passing       = rich.filter(s -> s.score >= 70);
                let passing_count = passing.len();
                let passing_total = passing.map(s -> s.score).reduce(a -> b -> a + b)(0);
                let passing_avg   = passing_total / passing_count;
                let top = rich.reduce(
                    best -> s -> if(s.score > best.score){ s }else{ best }
                )(rich[0]);
                let count_grade = label -> rich.filter(s -> s.grade == label).len();
                let subj_scores = subj -> rich.filter(s -> s.subject == subj).map(s -> s.score);
                let subj_avg    = subj -> {
                    let sc = subj_scores(subj);
                    sc.reduce(a -> b -> a + b)(0) / sc.len()
                };
                let ranked     = rich.sort(a -> b -> b.score - a.score);
                let rank_first = ranked.first();
                let rank_last  = ranked.last();
                (passing_count,
                 passing_avg,
                 top.name,
                 top.grade,
                 count_grade("A"),
                 count_grade("B"),
                 count_grade("C"),
                 subj_avg("Math"),
                 subj_avg("Science"),
                 rank_first.name,
                 rank_last.name)
                """);

        /*
         * Student scores: Alice=95(A), Bob=72(C), Charlie=88(B), Diana=61(F),
         *                 Eve=83(B), Frank=91(A), Grace=54(F)
         * Passing (>=70): Alice(95), Bob(72), Charlie(88), Eve(83), Frank(91) → 5 students
         * Passing total = 95+72+88+83+91 = 429; avg = 429/5 = 85
         * Grades: A=2, B=2, C=1, F=2  (Bob=C, Diana=F, Grace=F)
         * Math avg = (95+72+83+54)/4 = 304/4 = 76
         * Science avg = (88+61+91)/3 = 240/3 = 80
         */
        assertThat(v).isInstanceOf(TupleValue.class);
        TupleValue tv = (TupleValue) v;

        assertThat(RunnerHelper.intVal(tv.get(0))).isEqualTo(5L);          // passing count
        assertThat(RunnerHelper.intVal(tv.get(1))).isEqualTo(85L);         // passing avg
        assertThat(RunnerHelper.strVal(tv.get(2))).isEqualTo("Alice");     // top student name
        assertThat(RunnerHelper.strVal(tv.get(3))).isEqualTo("A");         // top student grade
        assertThat(RunnerHelper.intVal(tv.get(4))).isEqualTo(2L);          // A-grade count
        assertThat(RunnerHelper.intVal(tv.get(5))).isEqualTo(2L);          // B-grade count
        assertThat(RunnerHelper.intVal(tv.get(6))).isEqualTo(1L);          // C-grade count (Bob=72)
        assertThat(RunnerHelper.intVal(tv.get(7))).isEqualTo(76L);         // Math avg
        assertThat(RunnerHelper.intVal(tv.get(8))).isEqualTo(80L);         // Science avg
        assertThat(RunnerHelper.strVal(tv.get(9))).isEqualTo("Alice");     // rank 1
        assertThat(RunnerHelper.strVal(tv.get(10))).isEqualTo("Grace");    // rank last
    }

    // =========================================================================
    // 3. SUPER TEST 3 — Multi-Module Architecture
    //
    // Three modules: math_lib / text_lib / app
    //   math_lib  : exports gcd, factorial, is_prime, msort
    //   text_lib  : exports is_palindrome, longest_word, caesar_rot13
    //   app       : imports both, builds a combined report
    // =========================================================================
    @Test
    void test_super_multi_module() {
        Value v = RunnerHelper.runMultiModule(
                """
                module org.super.math
    
                let gcd  = a -> b -> if(b == 0){ a }else{ gcd(b)(a % b) };
                let _merge = a -> b -> {
                    if(a.len() == 0){ b }
                    else if(b.len() == 0){ a }
                    else{
                        let ha = a[0];
                        let hb = b[0];
                        let d  = ha - hb;
                        if(d <= 0){ [ha].concat(_merge(a.drop(1))(b)) }
                        else{       [hb].concat(_merge(a)(b.drop(1))) }
                    }
                };
                let msort = arr -> {
                    let n = arr.len();
                    if(n <= 1){ arr }
                    else{
                        let mid = n / 2;
                        _merge(msort(arr.take(mid)))(msort(arr.drop(mid)))
                    }
                };
    
                export gcd, msort;
                """,

                """
                module org.super.text
    
                let is_palindrome = s -> s == s.chars().reverse().reduce(a -> b -> a + b)("");
                let longest_word  = sentence -> {
                    let words  = sentence.split(" ");
                    let longer = a -> b -> if(a.len() > b.len()){ a }else{ b };
                    words.reduce(longer)("")
                };
                let word_count = sentence -> sentence.split(" ").len();
    
                export is_palindrome, word_count;
                """,

                """
                module org.super.app
    
                import gcd, msort from org.super.math;
                import is_palindrome, word_count from org.super.text;
    
                let g      = gcd(84)(36);
                let nums   = [10, 3, 7, 1, 8, 5, 9, 2, 6, 4];
                let sorted = msort(nums);
                let sentence  = "the quick brown fox jumps over the lazy dog";
                let wc        = word_count(sentence);
                let pal_check = is_palindrome("madam");
                (g, sorted.len(), pal_check, wc)
                """
        );

        /*
         * gcd(84, 36)  = 12
         * sorted len   = 10
         * is_palindrome("madam") = true
         * word count of "the quick brown fox jumps over the lazy dog" = 9
         */
        assertThat(v).isInstanceOf(TupleValue.class);
        TupleValue tv = (TupleValue) v;

        assertThat(RunnerHelper.intVal(tv.get(0))).isEqualTo(12L);   // gcd(84,36)
        assertThat(RunnerHelper.intVal(tv.get(1))).isEqualTo(10L);   // sorted array length
        assertThat(RunnerHelper.boolVal(tv.get(2))).isTrue();        // "madam" is palindrome
        assertThat(RunnerHelper.intVal(tv.get(3))).isEqualTo(9L);    // word count
    }

}
