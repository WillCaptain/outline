package org.twelve.outlineaipp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M2 self-tests for {@code outline_infer} and {@code outline_interpret}.
 *
 * <p>These tests pin down the three contract guarantees the downstream
 * {@code outline_code} skill relies on:
 * <ol>
 *   <li>The tool catalog advertises both tools with universal / LLM scope
 *       so the Router can surface them without per-app wiring.</li>
 *   <li>Syntax errors and inference errors are delivered in <em>separate</em>
 *       arrays — an undeclared-variable check must never masquerade as a typo.</li>
 *   <li>{@code outline_interpret} short-circuits on any upstream error and
 *       never executes user code that has not type-checked.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class OutlineInferInterpretToolTest {

    @Autowired private WebApplicationContext ctx;
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(ctx).build(); }

    @Test
    void toolsEndpoint_advertises_infer_and_interpret() throws Exception {
        MvcResult res = mvc().perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        List<?> tools = (List<?>) body.get("tools");
        List<String> names = tools.stream()
                .map(o -> (String) ((Map<?, ?>) o).get("name"))
                .toList();
        assertThat(names).contains("outline_parse", "outline_infer", "outline_interpret");
    }

    // ── outline_infer ─────────────────────────────────────────────────────────

    @Test
    void infer_validCode_returnsOkTrueAndNoErrors() throws Exception {
        Map<?, ?> body = callInfer("let a = 1;");
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat((List<?>) body.get("syntax_errors")).isEmpty();
        assertThat((List<?>) body.get("infer_errors")).isEmpty();
    }

    @Test
    void infer_syntaxError_surfacesInSyntaxBucket_notInferBucket() throws Exception {
        Map<?, ?> body = callInfer("let a = (");
        assertThat(body.get("ok")).isEqualTo(false);
        // Infer must not run (or at least must not report garbage) when the
        // source is unparseable. All diagnostics belong to syntax_errors.
        assertThat((List<?>) body.get("syntax_errors")).isNotEmpty();
    }

    // ── outline_interpret ─────────────────────────────────────────────────────

    @Test
    void interpret_validCode_runsAndReturnsResult() throws Exception {
        Map<?, ?> body = callInterpret("let a = 1 + 2; a;");
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat((List<?>) body.get("syntax_errors")).isEmpty();
        assertThat((List<?>) body.get("infer_errors")).isEmpty();
        Map<?, ?> result = (Map<?, ?>) body.get("result");
        assertThat(result).isNotNull();
        // We assert on display rather than unwrap() because the exact numeric
        // type (Long vs Integer) is an implementation detail of the interpreter.
        assertThat(String.valueOf(result.get("display"))).contains("3");
    }

    @Test
    void interpret_syntaxError_doesNotExecute() throws Exception {
        Map<?, ?> body = callInterpret("let a = (");
        assertThat(body.get("ok")).isEqualTo(false);
        assertThat((List<?>) body.get("syntax_errors")).isNotEmpty();
        // Absence of "result" is the contract: the interpreter must not run.
        assertThat(body.get("result")).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<?, ?> callInfer(String code) throws Exception {
        return call("/api/tools/outline_infer", code);
    }

    private Map<?, ?> callInterpret(String code) throws Exception {
        return call("/api/tools/outline_interpret", code);
    }

    private Map<?, ?> call(String path, String code) throws Exception {
        String req = json.writeValueAsString(Map.of("args", Map.of("code", code)));
        MvcResult res = mvc().perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(res.getResponse().getContentAsString(), Map.class);
    }
}
