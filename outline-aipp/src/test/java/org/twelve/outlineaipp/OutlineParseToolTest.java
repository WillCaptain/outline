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
 * M1 self-tests for {@code outline_parse}.
 *
 * <p>Cover three invariants the rest of the stack relies on:
 * <ul>
 *   <li>{@code /api/tools} advertises {@code outline_parse} with correct
 *       scope / visibility — otherwise the AIPP host cannot route to it.</li>
 *   <li>Valid code yields {@code ok:true} and no errors.</li>
 *   <li>Broken code yields {@code ok:false} with a non-empty error list
 *       (resilient mode — we don't care about the exact wording here, only
 *       that the tool does not throw 5xx).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class OutlineParseToolTest {

    @Autowired private WebApplicationContext ctx;
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(ctx).build(); }

    @Test
    void toolsEndpoint_advertises_outline_parse_with_universal_llm_scope() throws Exception {
        MvcResult res = mvc().perform(get("/api/tools"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        List<?> tools = (List<?>) body.get("tools");
        assertThat(tools).isNotEmpty();

        Map<?, ?> parseTool = tools.stream()
                .map(o -> (Map<?, ?>) o)
                .filter(m -> "outline_parse".equals(m.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("outline_parse not advertised"));

        assertThat(parseTool.get("visibility")).isEqualTo(List.of("llm"));
        Map<?, ?> scope = (Map<?, ?>) parseTool.get("scope");
        assertThat(scope.get("level")).isEqualTo("universal");
        assertThat(scope.get("visible_when")).isEqualTo("always");

        Map<?, ?> params = (Map<?, ?>) parseTool.get("parameters");
        assertThat((List<?>) params.get("required")).isEqualTo(List.of("code"));
    }

    @Test
    void invoke_validCode_returnsOkTrueAndNoErrors() throws Exception {
        String reqBody = json.writeValueAsString(Map.of("args", Map.of("code", "let a = 1;")));
        MvcResult res = mvc().perform(post("/api/tools/outline_parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("ok")).isEqualTo(true);
        assertThat((List<?>) body.get("errors")).isEmpty();
    }

    @Test
    void invoke_brokenCode_returnsOkFalseWithErrors() throws Exception {
        // Classic unclosed-paren case, exactly the example from the outline
        // diagnostics test. Resilient parsing must collect at least one error.
        String reqBody = json.writeValueAsString(Map.of("args", Map.of("code", "let a = (")));
        MvcResult res = mvc().perform(post("/api/tools/outline_parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqBody))
                .andExpect(status().isOk())           // the tool itself succeeded
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("ok")).isEqualTo(false);
        assertThat((List<?>) body.get("errors")).isNotEmpty();
    }

    @Test
    void invoke_unknownTool_returns404_withStructuredError() throws Exception {
        MvcResult res = mvc().perform(post("/api/tools/does_not_exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        assertThat(body.get("error")).isEqualTo("unknown_tool");
        assertThat(body.get("tool")).isEqualTo("does_not_exist");
    }
}
