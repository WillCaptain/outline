package org.twelve.outline.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal OpenAI-compatible {@link AskFn} — works against DeepSeek
 * ({@code https://api.deepseek.com/v1}) and any other
 * {@code /chat/completions}-shaped provider.
 *
 * <p>Deliberately free of Jackson / Spring deps so that
 * {@code outline-ai-plugin} stays small.  Configure via env:
 *
 * <pre>
 *   LLM_API_KEY   required
 *   LLM_BASE_URL  default https://api.deepseek.com/v1
 *   LLM_MODEL     default deepseek-chat   (overridden by the `model` arg)
 * </pre>
 */
public final class DeepSeekAsk implements AskFn {

    private static final Pattern CONTENT = Pattern.compile(
            "\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;

    public DeepSeekAsk(String baseUrl, String apiKey) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://api.deepseek.com/v1" : baseUrl.replaceAll("/+$", "");
        this.apiKey  = apiKey;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Reads {@code LLM_BASE_URL} / {@code LLM_API_KEY} from environment. */
    public static DeepSeekAsk fromEnv() {
        return new DeepSeekAsk(System.getenv("LLM_BASE_URL"),
                               System.getenv("LLM_API_KEY"));
    }

    @Override
    public AiResponse ask(String model, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AiResponse.Denied("no LLM_API_KEY configured");
        }
        String body = """
                {"model":"%s","messages":[{"role":"user","content":%s}],"max_tokens":512,"temperature":0.3}
                """.formatted(model, jsonString(prompt));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                return new AiResponse.Denied("HTTP " + resp.statusCode() + ": " + resp.body());
            }
            Matcher m = CONTENT.matcher(resp.body());
            String content = m.find() ? unescapeJson(m.group(1)) : "";
            return new AiResponse.Ok(content);
        } catch (java.net.http.HttpTimeoutException e) {
            return AiResponse.Timeout.INSTANCE;
        } catch (Exception e) {
            return new AiResponse.Denied(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/'  -> sb.append('/');
                    case 'u'  -> {
                        if (i + 4 < s.length()) {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default   -> sb.append(n);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }
}
