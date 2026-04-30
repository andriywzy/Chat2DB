package ai.chat2db.server.web.api.controller.ai.DocParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author CYY
 * @date March 20, 2023 8:13 am
 * @description
 */
public abstract class AbstractParser {

    protected static final int MAX_CHUNK_LENGTH = 200;
    private static final Pattern SENTENCE_SPLITTER = Pattern.compile("[。！？!?；;]+");

    public abstract List<String> parse(InputStream inputStream) throws Exception;

    protected List<String> buildChunks(String text) {
        String normalizedText = normalizeText(text);
        if (normalizedText.isBlank()) {
            return List.of();
        }

        String[] sentences = SENTENCE_SPLITTER.split(normalizedText);
        List<String> chunks = new ArrayList<>();
        for (String sentence : sentences) {
            appendChunk(chunks, sentence);
        }

        if (chunks.isEmpty()) {
            appendChunk(chunks, normalizedText);
        }
        return chunks;
    }

    protected void appendChunk(List<String> chunks, String text) {
        String chunk = normalizeText(text);
        if (chunk.length() < 5) {
            return;
        }
        if (chunk.length() <= MAX_CHUNK_LENGTH) {
            chunks.add(chunk);
            return;
        }
        for (int index = 0; index < chunk.length(); index += MAX_CHUNK_LENGTH) {
            int endIndex = Math.min(index + MAX_CHUNK_LENGTH, chunk.length());
            String substring = chunk.substring(index, endIndex).trim();
            if (substring.length() >= 5) {
                chunks.add(substring);
            }
        }
    }

    protected String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replaceAll("(\\r\\n|\\r|\\n|\\n\\r)+", "\n")
            .replaceAll("[\\t\\x0B\\f]+", " ")
            .replaceAll(" {2,}", " ")
            .trim();
    }
}
