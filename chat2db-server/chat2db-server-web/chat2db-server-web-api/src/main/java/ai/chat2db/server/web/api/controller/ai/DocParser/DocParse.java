package ai.chat2db.server.web.api.controller.ai.DocParser;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.InputStream;
import java.util.List;

public class DocParse extends AbstractParser {

    @Override
    public List<String> parse(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return buildChunks(extractor.getText());
        }
    }
}
