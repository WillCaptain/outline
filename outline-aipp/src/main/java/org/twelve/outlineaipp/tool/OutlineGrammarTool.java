package org.twelve.outlineaipp.tool;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code outline_grammar} — on-demand Outline language reference.
 *
 * <p>Works like a "{@code man}" page for Outline: the LLM asks for a specific
 * {@code section} (e.g. {@code cheatsheet}, {@code virtualset-and-this}) and
 * gets the full Markdown text back, plus the list of sibling sections so it
 * can navigate further without a second round-trip.
 *
 * <p>Sections live as classpath resources under
 * {@code /grammar/<name>.md}. Adding a new section is therefore a
 * documentation-only change: drop a new file, rebuild the jar, done — no
 * Java edits, no schema changes.
 */
@Component
public class OutlineGrammarTool implements ToolHandler {

    /** Classpath prefix for Outline grammar Markdown sections. */
    private static final String RESOURCE_PATTERN = "classpath:/grammar/*.md";

    /** Returned when the caller supplies no {@code section} argument. */
    private static final String DEFAULT_SECTION = "index";

    /**
     * Cached at construction time: section name → full Markdown body. The
     * grammar corpus is small (single-digit KB), stable across the process
     * lifetime, and read on nearly every skill invocation — fully loading
     * it into memory once at boot trades a few KB of heap for zero disk I/O
     * per tool call.
     */
    private final Map<String, String> sections;

    public OutlineGrammarTool() throws IOException {
        this.sections = loadSections();
    }

    private static Map<String, String> loadSections() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] files = resolver.getResources(RESOURCE_PATTERN);
        Map<String, String> out = new TreeMap<>();
        for (Resource r : files) {
            String file = r.getFilename();
            if (file == null || !file.endsWith(".md")) continue;
            String name = file.substring(0, file.length() - ".md".length());
            try (var in = r.getInputStream()) {
                out.put(name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @Override public String name() { return "outline_grammar"; }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name());

        List<String> available = sections.keySet().stream().sorted().toList();
        schema.put("description",
                "Fetch one section of the Outline language reference as raw Markdown. "
              + "Call with no `section` (or `section=index`) to get the table of contents. "
              + "Use when the inline cheat-sheet in the skill is not enough — e.g. you need "
              + "exact VirtualSet operator signatures or `~this` semantics. Available "
              + "sections: " + String.join(", ", available) + ".");

        Map<String, Object> sectionProp = new LinkedHashMap<>();
        sectionProp.put("type", "string");
        sectionProp.put("description", "Section name; omit to fetch the index.");
        sectionProp.put("enum", available);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("section", sectionProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", List.of());
        params.put("additionalProperties", false);
        schema.put("parameters", params);

        schema.put("visibility", List.of("llm"));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("level", "universal");
        scope.put("visible_when", "always");
        schema.put("scope", scope);
        return schema;
    }

    @Override
    public Map<String, Object> invoke(Map<String, Object> args) {
        String requested = null;
        if (args != null) {
            Object s = args.get("section");
            if (s instanceof String str && !str.isBlank()) requested = str.trim();
        }
        String section = requested == null ? DEFAULT_SECTION : requested;
        String body = sections.get(section);

        if (body == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "unknown_section");
            err.put("requested", section);
            err.put("available", sections.keySet().stream().sorted().toList());
            return err;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("section",   section);
        out.put("content",   body);
        // `siblings` saves the LLM one round-trip when it wants to page through
        // related topics (e.g. read cheatsheet → then pick a deeper section).
        out.put("siblings",  sections.keySet().stream().sorted().toList());
        return out;
    }

    /** Exposed for tests only. */
    Map<String, String> sectionsForTesting() { return sections; }
}
