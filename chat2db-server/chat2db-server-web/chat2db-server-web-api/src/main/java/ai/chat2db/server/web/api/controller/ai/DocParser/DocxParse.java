package ai.chat2db.server.web.api.controller.ai.DocParser;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.InputStream;
import java.util.List;

public class DocxParse extends AbstractParser {

    @Override
    public List<String> parse(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return buildChunks(extractor.getText());
        }
    }
}
