package org.twelve.outline.playground.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.twelve.outline.playground.model.ExampleCode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads built-in examples from {@code examples.json} on the classpath.
 * User-contributed examples are persisted to {@code user-examples.json}
 * next to the working directory (i.e. the project root in dev mode).
 */
@Component
public class ExamplesRepository {

    private static final Logger LOG = Logger.getLogger(ExamplesRepository.class.getName());
    private static final String USER_FILE = "user-examples.json";

    private final ObjectMapper mapper = new ObjectMapper();

    /** Ordered map: id → example. Built-in first, user-added after. */
    private final Map<String, ExampleCode> store = new LinkedHashMap<>();

    public ExamplesRepository() {
        loadBuiltIn();
        loadUserExamples();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<ExampleCode> findAll() {
        return new ArrayList<>(store.values());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persist a new example (or overwrite an existing one with the same id).
     * Changes are written to {@code user-examples.json} immediately.
     */
    public ExampleCode save(ExampleCode ex) {
        store.put(ex.id(), ex);
        persistUserExamples();
        return ex;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void loadBuiltIn() {
        try (InputStream in = new ClassPathResource("examples.json").getInputStream()) {
            List<ExampleCode> list = mapper.readValue(in, new TypeReference<>() {});
            list.forEach(e -> store.put(e.id(), e));
            LOG.info("Loaded " + list.size() + " built-in examples");
        } catch (IOException e) {
            LOG.warning("Could not load built-in examples.json: " + e.getMessage());
        }
    }

    private void loadUserExamples() {
        Path p = userFile();
        if (!p.toFile().exists()) return;
        try {
            List<ExampleCode> list = mapper.readValue(p.toFile(), new TypeReference<>() {});
            list.forEach(e -> store.put(e.id(), e));
            LOG.info("Loaded " + list.size() + " user examples from " + p);
        } catch (IOException e) {
            LOG.warning("Could not load user-examples.json: " + e.getMessage());
        }
    }

    private void persistUserExamples() {
        // Only write examples that are NOT in the built-in classpath file.
        List<ExampleCode> builtInIds = loadBuiltInIds();
        List<ExampleCode> user = store.values().stream()
                .filter(e -> builtInIds.stream().noneMatch(b -> b.id().equals(e.id())))
                .toList();
        try {
            File f = userFile().toFile();
            f.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(f, user);
        } catch (IOException e) {
            LOG.warning("Could not write user-examples.json: " + e.getMessage());
        }
    }

    private List<ExampleCode> loadBuiltInIds() {
        try (InputStream in = new ClassPathResource("examples.json").getInputStream()) {
            return mapper.readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    private Path userFile() {
        return Paths.get(System.getProperty("user.dir"), USER_FILE);
    }
}
