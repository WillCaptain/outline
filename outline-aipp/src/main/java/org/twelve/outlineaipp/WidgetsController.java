package org.twelve.outlineaipp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AIPP Widget endpoints. outline-aipp is a headless language service —
 * it has no widget / canvas surface. We still expose {@code GET /api/widgets}
 * returning an empty array so AIPP hosts can validate the contract uniformly.
 */
@RestController
@RequestMapping("/api")
public class WidgetsController {

    @GetMapping("/widgets")
    public Map<String, Object> widgets() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app",     ToolsController.APP_ID);
        result.put("version", ToolsController.APP_VERSION);
        result.put("widgets", List.of());
        return result;
    }
}
