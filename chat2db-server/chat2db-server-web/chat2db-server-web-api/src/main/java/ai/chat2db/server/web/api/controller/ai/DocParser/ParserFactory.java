package ai.chat2db.server.web.api.controller.ai.DocParser;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public final class ParserFactory {

    private static final Set<String> PLAIN_TEXT_TYPES = Set.of("txt", "md", "markdown", "csv");
    private static final Set<String> EXCEL_TYPES = Set.of("xls", "xlsx");
    private static final Set<String> WORD_TYPES = Set.of("doc", "docx");
    private static final Set<String> SUPPORTED_TYPES = Set.of("pdf", "txt", "md", "markdown", "csv", "doc", "docx", "xls", "xlsx");

    private ParserFactory() {
    }

    public static AbstractParser create(String fileType) {
        String normalizedType = normalizeFileType(fileType);
        if ("pdf".equals(normalizedType)) {
            return new PdfParse();
        }
        if (PLAIN_TEXT_TYPES.contains(normalizedType)) {
            return new PlainTextParse();
        }
        if ("docx".equals(normalizedType)) {
            return new DocxParse();
        }
        if ("doc".equals(normalizedType)) {
            return new DocParse();
        }
        if (EXCEL_TYPES.contains(normalizedType)) {
            return new ExcelParse();
        }
        throw new ParamBusinessException("file");
    }

    public static boolean supports(String fileType) {
        return SUPPORTED_TYPES.contains(normalizeFileType(fileType));
    }

    public static String normalizeFileType(String fileType) {
        if (StringUtils.isBlank(fileType)) {
            return "";
        }
        String normalized = StringUtils.lowerCase(fileType.trim());
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }
}
