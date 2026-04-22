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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M5 self-tests for the {@code outline_code} skill.
 *
 * <p>What we pin down:
 * <ol>
 *   <li>{@code /api/skills} advertises exactly one skill with the expected
 *       identity, the universal scope flags, and the full allowed-tools list.
 *       Drift here means the Router would either miss the skill entirely or
 *       load it with the wrong tool surface.</li>
 *   <li>The description passes Anthropic's {@code ≤1024} hard cap and carries
 *       a "Use when" trigger phrase — these are the signals world-one's Router
 *       reads to decide recall.</li>
 *   <li>The playbook endpoint serves the real {@code SKILL.md} body, YAML
 *       frontmatter included. The Router feeds this content verbatim to the
 *       executor LLM; a silent 404 or truncated body breaks skill execution.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "spring.main.banner-mode=off")
class OutlineCodeSkillTest {

    @Autowired private WebApplicationContext ctx;
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(ctx).build(); }

    @Test
    void skills_index_advertises_outline_code_with_correct_metadata() throws Exception {
        MvcResult res = mvc().perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        List<?> skills = (List<?>) body.get("skills");
        assertThat(skills).hasSize(1);

        Map<?, ?> s = (Map<?, ?>) skills.get(0);
        assertThat(s.get("name")).isEqualTo("outline_code");
        assertThat(s.get("level")).isEqualTo("universal");
        assertThat(s.get("owner_app")).isEqualTo("outline");
        assertThat(s.get("visible_when")).isEqualTo("always");
        assertThat(s.get("playbook_url")).isEqualTo("/api/skills/outline_code/playbook");

        @SuppressWarnings("unchecked")
        List<String> allowed = (List<String>) s.get("allowed_tools");
        assertThat(allowed).containsExactlyInAnyOrder(
                "outline_parse", "outline_infer", "outline_interpret",
                "outline_completion", "outline_grammar");
    }

    @Test
    void description_fits_anthropic_limits_and_signals_when_to_use() throws Exception {
        MvcResult res = mvc().perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andReturn();
        Map<?, ?> body = json.readValue(res.getResponse().getContentAsString(), Map.class);
        Map<?, ?> s = (Map<?, ?>) ((List<?>) body.get("skills")).get(0);
        String desc = (String) s.get("description");
        assertThat(desc.length()).isLessThanOrEqualTo(1024);
        assertThat(desc.toLowerCase()).contains("use when");
    }

    @Test
    void playbook_endpoint_serves_full_skill_md_body() throws Exception {
        MvcResult res = mvc().perform(get("/api/skills/outline_code/playbook"))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        // YAML frontmatter must be intact — Router relies on name/description
        // parsed from it, not only from the index entry.
        assertThat(body).startsWith("---");
        assertThat(body).contains("name: outline_code");
        assertThat(body).contains("allowed-tools:");
        // Workflow steps — the actual content that drives executor behaviour.
        assertThat(body).contains("Step 2 — Parse");
        assertThat(body).contains("Step 3 — Infer");
        assertThat(body).contains("outline_completion");
        assertThat(body).contains("outline_grammar");
    }

    @Test
    void unknown_playbook_id_returns_404() throws Exception {
        mvc().perform(get("/api/skills/does_not_exist/playbook"))
                .andExpect(status().isNotFound());
    }
}
