package com.vdt.aiops.monitoring.logcollector;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/* Normalize a log line into a template so identical-shape events group together */

@Component
public class LogTemplateExtractor {

        /*
         * Order MATTERS: specific -> generic. Integrity goal: strip only NOISE tokens
         * (timestamp/id/number/duration), KEEP discriminators (JSON keys, status class)
         * to avoid
         * collapsing distinct events into the same template.
         */
        private static final List<Rule> rules = List.of(
                        // 1. ISO-8601 timestamp: 2026-06-05T14:03:10.123Z | 2026-06-05 14:03:10+00:00
                        new Rule(Pattern.compile(
                                        "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?"),
                                        "<TS>"),
                        // 2. CLF/nginx timestamp: 05/Jun/2026:14:03:10
                        new Rule(Pattern.compile("\\d{2}/[A-Za-z]{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2}"), "<TS>"),
                        // 2b. Syslog/redis timestamp: 05 Jun 2026 06:55:23.727 (must run BEFORE
                        // standalone-time)
                        new Rule(Pattern.compile("\\b\\d{1,2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?"),
                                        "<TS>"),
                        // 3. Standalone time HH:MM:SS (no date)
                        new Rule(Pattern.compile("\\b\\d{2}:\\d{2}:\\d{2}\\b"), "<TS>"),
                        // 4. UUID / TraceID
                        new Rule(Pattern.compile(
                                        "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
                                        "<UUID>"),
                        // 5. IPv4 (optional port)
                        new Rule(Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b"), "<IP>"),
                        // 6. HEX/hash: 0x... or >=12 chars with AT LEAST one a-f letter
                        // (lookahead prevents swallowing long decimals, e.g. epoch-millis, as HEX)
                        new Rule(Pattern.compile(
                                        "\\b(?:0x[0-9a-fA-F]+|(?=[0-9a-fA-F]*[a-fA-F])[0-9a-fA-F]{12,})\\b"),
                                        "<HEX>"),
                        // 7. Quoted strings — VALUE only; (?!\\s*[:=]) KEEPS JSON/kv keys (won't turn
                        // "id": into <STR>)
                        new Rule(Pattern.compile("\"[^\"]*\"(?!\\s*[:=])"), "<STR>"),
                        new Rule(Pattern.compile("'[^']*'(?!\\s*[:=])"), "<STR>"),
                        // 8. Quantity WITH unit (duration/size) — before status so "256MB" isn't
                        // mistaken for a status
                        new Rule(Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:ms|us|ns|s|m|h|%|MB|KB|GB|TB|B)\\b"),
                                        "<DURATION>"),
                        // 9. HTTP status by class (letter tag -> survives <N>; "_4"/"_5" has no
                        // word-boundary so it's safe)
                        new Rule(Pattern.compile("\\b4\\d{2}\\b"), "<HTTP_4XX>"),
                        new Rule(Pattern.compile("\\b5\\d{2}\\b"), "<HTTP_5XX>"),
                        // 10. Remaining bare numbers (id, port, count, 2xx/3xx...) -> noise
                        new Rule(Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b"), "<N>"),
                        // 11. JSON/array of already-normalized values -> collapse variable-length array
                        // to <ARRAY>.
                        // Requires >=2 comma-separated placeholders, so it leaves tag-brackets
                        // ([ERROR], [<TS>]) intact.
                        new Rule(Pattern.compile(
                                        "\\[\\s*(?:<STR>|<N>|<DURATION>|<HEX>|<UUID>|<IP>|<TS>|<HTTP_[45]XX>|<ARRAY>)"
                                                        + "(?:\\s*,\\s*(?:<STR>|<N>|<DURATION>|<HEX>|<UUID>|<IP>|<TS>|<HTTP_[45]XX>|<ARRAY>))+\\s*\\]"),
                                        "<ARRAY>"),
                        // 12. JSON/struct object -> collapse to <JSON> (payload treated as noise;
                        // avoids one template per shape).
                        // Requires ':' or '=' inside braces so it leaves tag-braces ({main}, {<TS>})
                        // intact.
                        // NOTE: this drops intra-JSON structure at the seed; LogGroup.raw keeps one
                        // full sample,
                        // and the agent can pull raw lines via tool if the payload turns out
                        // diagnostic.
                        new Rule(Pattern.compile("\\{[^{}]*[:=][^{}]*\\}"), "<JSON>"));

        // Apply every rule in order, replacing matched tokens with their placeholder
        public String templateOf(String message) {
                if (message == null || message.isEmpty()) {
                        return "";
                }
                String t = message;
                for (Rule r : rules) {
                        t = r.getPattern().matcher(t).replaceAll(r.getReplacement());
                }
                return t.strip();
        }
}