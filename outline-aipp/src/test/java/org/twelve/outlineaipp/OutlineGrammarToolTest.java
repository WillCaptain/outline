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
 * M4 self-tests for {@code outline_grammar}.
 *
 * <p>Three invariants the skill relies on:
 * <ol>
 *   <li>All expected sections load from classpath. A missing section means
 *       the jar package step silently dropped a resource — catch it here,
 *       not at runtime in front of the LLM.</li>
 *   <li>No-argument invocation returns the index. This is the default
 *       entry-point the skill uses when it wants the table of contents.</li>
 *   <li>Unknown sections produce a structured error with the `available`
 *       list so the LLM can self-correct on the same turn.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class OutlineGrammarToolTest {

    @Autowired private WebApplicationContext ctx;
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(ctx).build(); }

    @Test
    void expected_sections_are_all_packaged() throws Exception {
        Map<?, ?> idx = call(null);
        @SuppressWarnings("unchecked")
        List<String> siblings = (List<String>) idx.get("siblings");
        assertThat(siblings).contains(
                "index", "cheatsheet", "values-and-types",
                "lambdas-and-functions", "outline-decl",
                "control-flow", "modules", "philosophy");
        // Ontology / VirtualSet / ~this documentation lives in the world-entitir
        // grammar, not here — universal outline_grammar must stay language-only.
        assertThat(siblings).doesNotContain("virtualset-and-this");
    }

    @Test
    void no_arg_returns_index_section() throws Exception {
        Map<?, ?> body = call(null);
        assertThat(body.get("section")).isEqualTo("index");
        String content = (String) body.get("content");
        // Index doc is the routing table — must link to the other sections.
        assertThat(content).contains("cheatsheet");
        assertThat(content).contains("outline-decl");
    }

    @Test
    void virtualset_section_is_not_served_by_universal_grammar() throws Exception {
        // Regression guard: virtualset / ontology knowledge must not leak into
        // the universal outline-aipp grammar. It belongs to world-entitir.
        Map<?, ?> body = call("virtualset-and-this");
        assertThat(body.get("error")).isEqualTo("unknown_section");
    }

    @Test
    void unknown_section_returns_structured_error_with_available_list() throws Exception {
        Map<?, ?> body = call("does-not-exist");
        assertThat(body.get("error")).isEqualTo("unknown_section");
        assertThat(body.get("requested")).isEqualTo("does-not-exist");
        // The LLM uses `available` to self-correct on the next turn.
        @SuppressWarnings("unchecked")
        List<String> available = (List<String>) body.get("available");
        assertThat(available).isNotEmpty().contains("index");
    }

    private Map<?, ?> call(String section) throws Exception {
        Map<String, Object> args = section == null ? Map.of() : Map.of("section", section);
        String req = json.writeValueAsString(Map.of("args", args));
        MvcResult res = mvc().perform(post("/api/tools/outline_grammar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isOk())
                .andReturn();
        return json.readValue(res.getResponse().getContentAsString(), Map.class);
    }
}
