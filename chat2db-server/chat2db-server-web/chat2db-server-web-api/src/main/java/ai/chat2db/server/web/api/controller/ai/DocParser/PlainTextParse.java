package ai.chat2db.server.web.api.controller.ai.DocParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PlainTextParse extends AbstractParser {

    @Override
    public List<String> parse(InputStream inputStream) throws Exception {
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        return buildChunks(content);
    }
}
