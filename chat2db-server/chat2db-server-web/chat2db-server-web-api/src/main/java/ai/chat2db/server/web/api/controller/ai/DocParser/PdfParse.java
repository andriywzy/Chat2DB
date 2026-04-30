package ai.chat2db.server.web.api.controller.ai.DocParser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author CYY
 * @date March 11, 2023 3:23 pm
 * @description
 */
public class PdfParse extends AbstractParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParse.class);

    private static final float OCR_DPI = 240F;
    private static final long OCR_TIMEOUT_SECONDS = 120L;
    private static final String DEFAULT_OCR_COMMAND = "tesseract";
    private static final String DEFAULT_OCR_LANGUAGES = "chi_sim+eng";
    private static final String OCR_TESSDATA_DIR_PROPERTY = "chat2db.ocr.tessdata-dir";
    private static final String OCR_TESSDATA_DIR_ENV = "CHAT2DB_OCR_TESSDATA_DIR";

    @Override
    public List<String> parse(InputStream inputStream) throws IOException {
        try (PDDocument document = PDDocument.load(inputStream)) {
            String text = extractText(document);
            if (text.isBlank()) {
                log.info("pdf text extractor returned empty text, falling back to OCR");
                text = ocrText(document);
            }
            return buildChunks(text);
        }
    }

    private String extractText(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        return normalizeText(stripper.getText(document));
    }

    private String ocrText(PDDocument document) {
        StringBuilder combinedText = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(document);
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            try {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_DPI, ImageType.GRAY);
                String pageText = runTesseract(image);
                if (!pageText.isBlank()) {
                    if (combinedText.length() > 0) {
                        combinedText.append('\n');
                    }
                    combinedText.append(pageText);
                }
            } catch (Exception e) {
                log.warn("ocr fallback failed on pdf page {}", pageIndex, e);
            }
        }
        return normalizeText(combinedText.toString());
    }

    private String runTesseract(BufferedImage image) throws IOException, InterruptedException {
        File imageFile = File.createTempFile("chat2db-ocr-", ".png");
        try {
            ImageIO.write(image, "png", imageFile);
            ProcessBuilder processBuilder = new ProcessBuilder(
                getOcrCommand(),
                imageFile.getAbsolutePath(),
                "stdout",
                "--psm",
                "6"
            );
            String tessdataDir = getOcrTessdataDir();
            if (!tessdataDir.isBlank()) {
                processBuilder.command().add("--tessdata-dir");
                processBuilder.command().add(tessdataDir);
            }
            processBuilder.command().add("-l");
            processBuilder.command().add(getOcrLanguages());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Tesseract OCR timed out");
            }
            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());
            if (process.exitValue() != 0) {
                log.warn("tesseract exited with code {}: {}", process.exitValue(), error);
                return "";
            }
            return output;
        } finally {
            imageFile.delete();
        }
    }

    private String getOcrCommand() {
        return System.getProperty(
            "chat2db.ocr.command",
            System.getenv().getOrDefault("CHAT2DB_OCR_COMMAND", DEFAULT_OCR_COMMAND)
        );
    }

    private String getOcrLanguages() {
        return System.getProperty(
            "chat2db.ocr.languages",
            System.getenv().getOrDefault("CHAT2DB_OCR_LANGUAGES", DEFAULT_OCR_LANGUAGES)
        );
    }

    private String getOcrTessdataDir() {
        return System.getProperty(
            OCR_TESSDATA_DIR_PROPERTY,
            System.getenv().getOrDefault(OCR_TESSDATA_DIR_ENV, "")
        ).trim();
    }
}
