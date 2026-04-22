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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3 self-tests for {@code outline_completion}.
 *
 * <p>We check three shapes that the {@code outline_code} skill relies on:
 * <ol>
 *   <li>Top-level record value — the prototype "what can I do with this thing"
 *       case. All declared fields must appear in the items list.</li>
 *   <li>Lambda parameter — the critical case that motivates the whole tool:
 *       inside {@code filter(a -> a.)}, {@code a} is bound to the element type
 *       of the collection and cannot be found in the top-level symbol table.</li>
 *   <li>Non-typeable prefix — empty {@code items}, never a 5xx. Stability
 *       guarantee: the LLM hits many incomplete prefixes and must not be
 *       penalised with exceptions.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class OutlineCompletionToolTest {

    @Autowired private WebApplicationContext ctx;
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(ctx).build(); }

    @Test
    void dot_after_record_variable_lists_all_fields() throws Exception {
        String code = "let p = {name: \"will\", age: 10}; p.";
        List<Map<String, Object>> items = callAt(code, -1);
        List<String> labels = items.stream().map(m -> (String) m.get("label")).toList();
        assertThat(labels).contains("name", "age");
    }

    @Test
    void dot_inside_lambda_resolves_parameter_type() throws Exception {
        // The cursor is positioned right after "a." inside the lambda body.
        // The top-level symbol env has `xs` but not `a` — `a` is a lambda
        // parameter, so the engine must walk the AST by lexeme to find it.
        String code = "let xs = [{name: \"will\", age: 10}]; xs.filter(a -> a.";
        List<Map<String, Object>> items = callAt(code, -1);
        List<String> labels = items.stream().map(m -> (String) m.get("label")).toList();
        // Either the record fields (ideal) or at least a non-empty builtin set
        // (Int/String operators on the element type). We assert the record-field
        // path because that is the feature the skill needs.
        assertThat(labels).contains("name", "age");
    }

    @Test
    void empty_items_when_prefix_cannot_be_typed() throws Exception {
        // Pure gibberish — must not throw, must return an empty items array.
        List<Map<String, Object>> items = callAt("$$$.", -1);
        assertThat(items).isEmpty();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callAt(String code, int offset) throws Exception {
        Map<String, Object> args = offset < 0
                ? Map.of("code", code)
                : Map.of("code", code, "offset", offset);
        String req = json.writeValueAsString(Map.of("args", args));
        MvcResult res = mvc().perform(post("/api/tools/outline_completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        return (List<Map<String, Object>>) body.get("items");
    }
}
