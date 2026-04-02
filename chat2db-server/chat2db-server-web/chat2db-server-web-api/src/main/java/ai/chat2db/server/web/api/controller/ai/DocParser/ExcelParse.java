package ai.chat2db.server.web.api.controller.ai.DocParser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ExcelParse extends AbstractParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public List<String> parse(InputStream inputStream) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<String> rows = new ArrayList<>();
            for (Sheet sheet : workbook) {
                rows.add("Sheet: " + sheet.getSheetName());
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = normalizeText(dataFormatter.formatCellValue(cell));
                        if (!value.isBlank()) {
                            cells.add(value);
                        }
                    }
                    if (!cells.isEmpty()) {
                        rows.add(String.join(" | ", cells));
                    }
                }
            }
            return buildChunks(String.join("\n", rows));
        }
    }
}
