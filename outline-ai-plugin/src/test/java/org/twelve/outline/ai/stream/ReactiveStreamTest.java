package org.twelve.outline.ai.stream;

import org.junit.jupiter.api.Test;
import org.twelve.gcp.ast.ASF;
import org.twelve.gcp.ast.AST;
import org.twelve.gcp.interpreter.OutlineInterpreter;
import org.twelve.gcp.interpreter.value.Value;
import org.twelve.outline.OutlineParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Minimal reactive/lazy stream contract:
 *
 * <ul>
 *   <li>non-terminal operators (currently only {@code map}) create a new stream
 *       stage that records the current {@code Operator} and the previous stream
 *       instance as upstream;</li>
 *   <li>terminal {@code subscribe} walks the upstream chain and pushes source
 *       elements through each stage in declaration order;</li>
 *   <li>there is no eager computation while building the chain.</li>
 * </ul>
 */
public class ReactiveStreamTest {

    private Value run(String source) {
        OutlineParser parser = new OutlineParser();
        AST ast = parser.parse(new ASF(), source);
        ast.asf().infer();
        return new OutlineInterpreter().run(ast.asf());
    }

    private static final String STREAM_PRELUDE = readStdlib("/stdlib/stream.outline");

    private static String readStdlib(String path) {
        try (var in = ReactiveStreamTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void map_is_non_terminal_and_subscribe_drives_source_to_sink() {
        String src = STREAM_PRELUDE + """
                let log = {var text = ""};
                let s = stream([1, 2, 3])
                    .map(x -> x + 1)
                    .map(x -> x * 10);

                // Nothing has run yet: map only records stages.
                let before = log.text;

                s.subscribe(x -> log.text = log.text + to_str(x) + ",");
                (before, log.text)
                """;

        Value v = run(src);
        assertEquals("(\"\",\"20,30,40,\")", v.toString());
    }

    @Test
    void two_streams_share_upstream_without_interfering() {
        String src = STREAM_PRELUDE + """
                let base = stream([1, 2]);
                let a = base.map(x -> x + 10);
                let b = base.map(x -> x * 10);
                let log = {var text = ""};

                a.subscribe(x -> log.text = log.text + "a" + to_str(x) + ";");
                b.subscribe(x -> log.text = log.text + "b" + to_str(x) + ";");
                log.text
                """;

        Value v = run(src);
        assertEquals("\"a11;a12;b10;b20;\"", v.toString());
    }
}
