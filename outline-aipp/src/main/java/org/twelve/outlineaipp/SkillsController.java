package org.twelve.outlineaipp;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AIPP Skill endpoints — Anthropic Skills progressive-disclosure model.
 *
 * <ul>
 *   <li>{@code GET  /api/skills}               — skill index
 *       ({@code name} + {@code description} + {@code allowed_tools} + scope).</li>
 *   <li>{@code GET  /api/skills/{id}/playbook} — full {@code SKILL.md} Markdown.</li>
 * </ul>
 *
 * <p>The {@code description} field is the sole recall signal the Router LLM
 * uses to decide whether to load the skill — we run a startup lint to keep
 * it within Anthropic's ≤1024 char window and to flag missing "Use when..."
 * clauses early, before any user traffic can hit a low-quality description.
 */
@RestController
@RequestMapping("/api")
public class SkillsController {

    /** Skills known by this app — playbook files under {@code resources/skills/{id}/SKILL.md}. */
    private static final Set<String> KNOWN_PLAYBOOK_IDS = Set.of("outline_code");

    @GetMapping("/skills")
    public Map<String, Object> skills() {
        List<Map<String, Object>> all = List.of(buildOutlineCodeSkillIndex());
        for (Map<String, Object> s : all) lintSkillIndexEntry(s);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app",     ToolsController.APP_ID);
        result.put("version", ToolsController.APP_VERSION);
        result.put("skills",  all);
        return result;
    }

    /**
     * {@code outline_code} — the skill catalog entry the Router reads.
     *
     * <p>Fields are Anthropic-native; {@code level}, {@code owner_app} and
     * {@code visible_when} are our own additions so world-one's Router can
     * filter by UI context (this one is universal — Outline authoring is
     * relevant in any session that has text input).
     */
    private static Map<String, Object> buildOutlineCodeSkillIndex() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", "outline_code");
        s.put("description",
            "Author, type-check and execute standalone Outline language snippets with a tight " +
            "diagnostic loop. Use when the user asks to \"write / fix / debug / run Outline code\" " +
            "at the language level (e.g. \"帮我写一段 outline 计算斐波那契\", \"这段 outline 代码能编译吗\", " +
            "\"outline lambda / match / fx 怎么写\", \"check if this outline compiles\"). " +
            "Covers the whole draft → parse → infer → interpret loop, uses `outline_completion` " +
            "to discover members at every `.` anchor, and pulls from `outline_grammar` sections on demand. " +
            "This skill knows Outline as a pure language only — it has NO knowledge of any ontology, world, " +
            "VirtualSet collection, or `__ontology_repo__` binding. If the user's request depends on concrete " +
            "ontology types (decision triggers, actions, virtualset queries over live entities), stop and say so " +
            "— a domain skill in the owning app handles that.");
        s.put("allowed_tools", List.of(
                "outline_parse",
                "outline_infer",
                "outline_interpret",
                "outline_completion",
                "outline_grammar"));
        s.put("level",        "universal");
        s.put("owner_app",    ToolsController.APP_ID);
        s.put("visible_when", "always");
        s.put("playbook_url", "/api/skills/outline_code/playbook");
        return s;
    }

    /**
     * Startup / response-time lint — mirrors the one in {@code world-entitir}'s
     * {@code SkillsController}. Hard constraints throw; soft constraints warn.
     */
    static void lintSkillIndexEntry(Map<String, Object> entry) {
        Object nameObj = entry.get("name");
        if (!(nameObj instanceof String name) || name.isBlank()) {
            throw new IllegalStateException("Skill index entry missing 'name'");
        }
        Object descObj = entry.get("description");
        if (!(descObj instanceof String desc) || desc.isBlank()) {
            throw new IllegalStateException("Skill '" + name + "' missing 'description'");
        }
        if (desc.length() > 1024) {
            throw new IllegalStateException(
                    "Skill '" + name + "' description too long ("
                            + desc.length() + " > 1024) — Anthropic hard limit");
        }
        if (desc.length() < 40) {
            System.err.println("[skill-lint] WARN skill '" + name
                    + "' description is short (" + desc.length() + " < 40).");
        }
        String lower = desc.toLowerCase(Locale.ROOT);
        boolean hasWhen = lower.contains("use when") || lower.contains("用于")
                || lower.contains("当用户") || lower.contains("when the user")
                || lower.contains("when ");
        if (!hasWhen) {
            System.err.println("[skill-lint] WARN skill '" + name
                    + "' description does not state WHEN to use it.");
        }
        Object tools = entry.get("allowed_tools");
        if (!(tools instanceof List<?> tl) || tl.isEmpty()) {
            throw new IllegalStateException(
                    "Skill '" + name + "' must declare non-empty allowed_tools");
        }
    }

    /**
     * Serve a single skill's {@code SKILL.md} body (Anthropic Skills format:
     * YAML frontmatter + Markdown procedure).
     */
    @GetMapping(value = "/skills/{id}/playbook",
                produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> playbook(@PathVariable("id") String id) {
        if (!KNOWN_PLAYBOOK_IDS.contains(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body("Skill playbook not found: " + id);
        }
        String resourcePath = "/skills/" + id + "/SKILL.md";
        try (var in = SkillsController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .header("Content-Type", "text/plain;charset=UTF-8")
                        .body("Skill playbook resource missing: " + resourcePath);
            }
            String body = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/markdown;charset=UTF-8")
                    .body(body);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body("Failed to read playbook: " + e.getMessage());
        }
    }
}
