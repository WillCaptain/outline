package org.twelve.outlineaipp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * outline-aipp — an AIPP host-app that wraps the Outline language core
 * (parser + GCP inference + interpreter) as a bundle of atomic tools
 * and a single {@code outline_code} skill.
 *
 * <p>Conforms to the AIPP protocol:
 * <ul>
 *   <li>{@code GET  /api/tools}                — tool catalog with
 *       {@code visibility} + {@code scope} metadata</li>
 *   <li>{@code POST /api/tools/{name}}         — tool execution</li>
 *   <li>{@code GET  /api/skills}               — skill playbook index</li>
 *   <li>{@code GET  /api/skills/{id}/playbook} — single playbook (Markdown)</li>
 *   <li>{@code GET  /api/widgets}              — widget manifest (stub, no canvas)</li>
 * </ul>
 *
 * <p>Stateless service — no database, no session. Every tool invocation is
 * a pure function of its arguments (parse / infer / complete / interpret).
 * JDBC auto-configuration is excluded so absent JDBC deps never block boot.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class OutlineAippApplication {
    public static void main(String[] args) {
        SpringApplication.run(OutlineAippApplication.class, args);
    }
}
