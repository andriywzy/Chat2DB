package ai.chat2db.server.web.api.controller.ai.platform.sql;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlOnlyResponseSanitizer {

    private static final Pattern SQL_BLOCK_PATTERN = Pattern.compile("(?is)```(?:sql)?\\s*(.*?)```");
    private static final Pattern SQL_START_PATTERN = Pattern.compile(
        "(?is)\\b(with|select|insert|update|delete|create|alter|drop|truncate|merge|call|exec|show|desc|describe|use)\\b"
    );
    private static final Pattern MULTI_LINE_COMMENT_PATTERN = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern INLINE_SQL_COMMENT_PATTERN = Pattern.compile("(?m)\\s--.*$");
    private static final Pattern HASH_COMMENT_PATTERN = Pattern.compile("(?m)^\\s*#.*$");

    public String sanitize(String rawContent) {
        if (StringUtils.isBlank(rawContent)) {
            return null;
        }

        String candidate = extractSqlBlock(rawContent);
        if (StringUtils.isBlank(candidate)) {
            candidate = rawContent;
        }

        candidate = candidate
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim();
        candidate = removeLeadingNarrative(candidate);
        candidate = removeMarkdownFence(candidate);
        candidate = removeComments(candidate);
        candidate = stripLeadingLabels(candidate);
        candidate = trimNarrativeTail(candidate);
        candidate = candidate.trim();

        return isValidSql(candidate) ? candidate : null;
    }

    public boolean isValidSql(String content) {
        if (StringUtils.isBlank(content)) {
            return false;
        }
        String normalized = content.trim();
        Matcher matcher = SQL_START_PATTERN.matcher(normalized);
        return matcher.find() && matcher.start() == 0;
    }

    public String buildRetryPrompt(String prompt) {
        return prompt + "\n\n"
            + "You must retry and output only executable SQL for the current datasource dialect.\n"
            + "Do not include any explanation, comments, markdown code fences, prefixes, suffixes, or natural language.\n"
            + "Return the SQL only.";
    }

    private String extractSqlBlock(String content) {
        Matcher matcher = SQL_BLOCK_PATTERN.matcher(content);
        String fallback = null;
        while (matcher.find()) {
            String block = StringUtils.trimToEmpty(matcher.group(1));
            if (StringUtils.isBlank(block)) {
                continue;
            }
            if (isValidSql(removeComments(removeMarkdownFence(block)).trim())) {
                return block;
            }
            if (fallback == null) {
                fallback = block;
            }
        }
        return fallback;
    }

    private String removeLeadingNarrative(String content) {
        Matcher matcher = SQL_START_PATTERN.matcher(content);
        if (!matcher.find()) {
            return content;
        }
        return content.substring(matcher.start());
    }

    private String removeMarkdownFence(String content) {
        return content
            .replace("```sql", StringUtils.EMPTY)
            .replace("```SQL", StringUtils.EMPTY)
            .replace("```", StringUtils.EMPTY)
            .trim();
    }

    private String removeComments(String content) {
        String candidate = MULTI_LINE_COMMENT_PATTERN.matcher(content).replaceAll(StringUtils.EMPTY);
        candidate = HASH_COMMENT_PATTERN.matcher(candidate).replaceAll(StringUtils.EMPTY);
        candidate = INLINE_SQL_COMMENT_PATTERN.matcher(candidate).replaceAll(StringUtils.EMPTY);

        List<String> cleanedLines = new ArrayList<>();
        for (String line : candidate.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            cleanedLines.add(trimRight(line));
        }
        return String.join("\n", cleanedLines).trim();
    }

    private String stripLeadingLabels(String content) {
        String candidate = content.trim();
        String lowerCase = candidate.toLowerCase();
        if (lowerCase.startsWith("sql:")) {
            return candidate.substring(4).trim();
        }
        if (lowerCase.startsWith("answer:")) {
            return candidate.substring(7).trim();
        }
        return candidate;
    }

    private String trimNarrativeTail(String content) {
        if (StringUtils.isBlank(content)) {
            return content;
        }
        String[] lines = content.split("\n");
        List<String> keptLines = new ArrayList<>();
        boolean started = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!started) {
                if (StringUtils.isBlank(trimmed)) {
                    continue;
                }
                started = true;
            }
            if (started && looksLikeTrailingNarrative(trimmed)) {
                break;
            }
            keptLines.add(trimRight(line));
        }
        return String.join("\n", keptLines).trim();
    }

    private boolean looksLikeTrailingNarrative(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        String lowerLine = line.toLowerCase();
        return lowerLine.startsWith("explanation:")
            || lowerLine.startsWith("note:")
            || lowerLine.startsWith("说明")
            || lowerLine.startsWith("解释")
            || lowerLine.startsWith("以上")
            || lowerLine.startsWith("the sql")
            || lowerLine.startsWith("this sql");
    }

    private String trimRight(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
