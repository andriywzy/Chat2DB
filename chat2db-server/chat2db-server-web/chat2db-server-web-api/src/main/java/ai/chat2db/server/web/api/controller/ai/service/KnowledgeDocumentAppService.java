package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.domain.api.enums.KnowledgeDocumentStatusEnum;
import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentCreateParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentUpdateParam;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.ConfigUtils;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.ai.DocParser.AbstractParser;
import ai.chat2db.server.web.api.controller.ai.DocParser.ParserFactory;
import ai.chat2db.server.web.api.controller.ai.platform.embedding.AiEmbeddingService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeDocumentAppService {

    private static final Integer PREVIEW_LIMIT = 8000;
    private static final String KNOWLEDGE_DIRECTORY = "knowledge";
    private static final String SUPPORTED_FORMATS = "PDF, MD, MARKDOWN, TXT, DOC, DOCX, XLS, XLSX, CSV";

    @Autowired
    private AiEmbeddingService aiEmbeddingService;

    @Autowired
    private GatewayClientService gatewayClientService;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    public DataResult<KnowledgeDocument> upload(String name, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return DataResult.error("knowledge.document.file.empty", "Please select a non-empty file to upload.");
        }
        String fileName = file.getOriginalFilename();
        String fileType = getFileType(fileName);
        if (!ParserFactory.supports(fileType)) {
            return DataResult.error(
                "knowledge.document.file.unsupported",
                buildUnsupportedFileTypeMessage(fileName, fileType)
            );
        }

        Long userId = ContextUtils.getUserId();
        String documentName = StringUtils.isNotBlank(name) ? name : stripExtension(fileName);
        Long documentId = knowledgeDocumentService.create(buildCreateParam(documentName, fileName, fileType)).getData();
        String storagePath = buildStoragePath(userId, documentId, fileType);

        try {
            saveLocalFile(file, storagePath);
            updateStoragePath(documentId, storagePath);
            return processDocument(documentId, userId, storagePath, fileType, false);
        } catch (Exception e) {
            updateAsFailed(documentId, 0, 0, null, e.getMessage(), storagePath);
            throw e;
        }
    }

    public ActionResult delete(Long id) {
        KnowledgeDocument document = queryOwnedDocument(id);
        ActionResult result = deleteRemoteVectors(document);
        if (!result.success()) {
            return result;
        }
        deleteLocalFile(document.getStoragePath());
        return knowledgeDocumentService.deleteWithPermission(id);
    }

    public DataResult<KnowledgeDocument> rebuild(Long id) throws Exception {
        KnowledgeDocument document = queryOwnedDocument(id);
        String storagePath = document.getStoragePath();
        if (StringUtils.isBlank(storagePath) || !Files.exists(Paths.get(storagePath))) {
            return DataResult.error("knowledge.document.storage.not.found", "Knowledge source file not found");
        }
        updateAsProcessing(id, storagePath);
        return processDocument(id, document.getUserId(), storagePath, document.getFileType(), true);
    }

    private KnowledgeDocumentCreateParam buildCreateParam(String documentName, String fileName, String fileType) {
        KnowledgeDocumentCreateParam param = new KnowledgeDocumentCreateParam();
        param.setName(documentName);
        param.setFileName(fileName);
        param.setFileType(fileType);
        param.setStatus(KnowledgeDocumentStatusEnum.PROCESSING.name());
        param.setSentenceCount(0);
        param.setWordCount(0);
        param.setVectorCount(0);
        return param;
    }

    private DataResult<KnowledgeDocument> processDocument(
        Long documentId,
        Long userId,
        String storagePath,
        String fileType,
        boolean deleteExistingVectors
    )
        throws Exception {
        List<String> sentenceList = new ArrayList<>();
        String preview = null;
        Integer wordCount = 0;

        try (InputStream inputStream = Files.newInputStream(Paths.get(storagePath))) {
            AbstractParser parser = ParserFactory.create(fileType);
            sentenceList = parser.parse(inputStream).stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
            preview = buildPreview(sentenceList);
            wordCount = sentenceList.stream().mapToInt(String::length).sum();

            if (CollectionUtils.isEmpty(sentenceList)) {
                String errorMessage = buildNoExtractableTextMessage(fileType);
                updateAsFailed(documentId, 0, 0, null, errorMessage, storagePath);
                return DataResult.error("knowledge.document.text.empty", errorMessage);
            }

            if (!aiEmbeddingService.supportsCurrentProvider()) {
                String errorMessage = buildEmbeddingUnsupportedMessage();
                updateAsFailed(documentId, sentenceList.size(), wordCount, preview, errorMessage, storagePath);
                return DataResult.error("knowledge.document.embedding.unsupported", errorMessage);
            }

            List<List<BigDecimal>> contentVector = buildEmbeddingVectors(sentenceList);
            if (CollectionUtils.isEmpty(contentVector)) {
                String errorMessage = buildNoEmbeddingVectorMessage(fileType);
                updateAsFailed(documentId, sentenceList.size(), wordCount, preview, errorMessage, storagePath);
                return DataResult.error("knowledge.document.embedding.empty", errorMessage);
            }

            if (deleteExistingVectors) {
                ActionResult deleteResult = gatewayClientService.knowledgeVectorDelete(buildKnowledgeRequest(documentId, userId, null, null));
                if (!deleteResult.success()) {
                    updateAsFailed(documentId, sentenceList.size(), wordCount, preview, deleteResult.getErrorMessage(), storagePath);
                    return DataResult.error(
                        StringUtils.defaultIfBlank(deleteResult.getErrorCode(), "knowledge.document.rebuild.failed"),
                        StringUtils.defaultIfBlank(deleteResult.getErrorMessage(), "Knowledge rebuild failed")
                    );
                }
            }

            ActionResult result = gatewayClientService.knowledgeVectorSave(
                buildKnowledgeRequest(documentId, userId, sentenceList, contentVector)
            );
            if (!result.success()) {
                updateAsFailed(documentId, sentenceList.size(), wordCount, preview, result.getErrorMessage(), storagePath);
                return DataResult.error(
                    StringUtils.defaultIfBlank(result.getErrorCode(), "knowledge.document.upload.failed"),
                    StringUtils.defaultIfBlank(result.getErrorMessage(), "Knowledge upload failed")
                );
            }

            KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
            updateParam.setId(documentId);
            updateParam.setStoragePath(storagePath);
            updateParam.setStatus(KnowledgeDocumentStatusEnum.READY.name());
            updateParam.setSentenceCount(sentenceList.size());
            updateParam.setWordCount(wordCount);
            updateParam.setVectorCount(contentVector.size());
            updateParam.setContentPreview(preview);
            updateParam.setErrorMessage(null);
            knowledgeDocumentService.update(updateParam);
            return knowledgeDocumentService.queryExistent(documentId);
        } catch (Exception e) {
            updateAsFailed(documentId, sentenceList.size(), wordCount, preview, e.getMessage(), storagePath);
            throw e;
        }
    }

    private List<List<BigDecimal>> buildEmbeddingVectors(List<String> sentenceList) {
        List<List<BigDecimal>> contentVector = new ArrayList<>();
        for (String sentence : sentenceList) {
            AiEmbeddingResponse response = aiEmbeddingService.embed(sentence);
            if (response == null || CollectionUtils.isEmpty(response.getVectors())) {
                continue;
            }
            contentVector.add(response.getVectors().get(0));
        }
        return contentVector;
    }

    private KnowledgeRequest buildKnowledgeRequest(
        Long documentId,
        Long userId,
        List<String> sentenceList,
        List<List<BigDecimal>> contentVector
    ) {
        KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
        knowledgeRequest.setDocumentId(documentId);
        knowledgeRequest.setUserId(userId);
        knowledgeRequest.setSentenceList(sentenceList);
        knowledgeRequest.setContentVector(contentVector);
        return knowledgeRequest;
    }

    private void updateAsProcessing(Long documentId, String storagePath) {
        KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
        updateParam.setId(documentId);
        updateParam.setStoragePath(storagePath);
        updateParam.setStatus(KnowledgeDocumentStatusEnum.PROCESSING.name());
        updateParam.setErrorMessage(null);
        knowledgeDocumentService.update(updateParam);
    }

    private void updateStoragePath(Long documentId, String storagePath) {
        KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
        updateParam.setId(documentId);
        updateParam.setStoragePath(storagePath);
        knowledgeDocumentService.update(updateParam);
    }

    private void updateAsFailed(
        Long documentId,
        Integer sentenceCount,
        Integer wordCount,
        String preview,
        String errorMessage,
        String storagePath
    ) {
        KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
        updateParam.setId(documentId);
        updateParam.setStoragePath(storagePath);
        updateParam.setStatus(KnowledgeDocumentStatusEnum.FAILED.name());
        updateParam.setSentenceCount(sentenceCount);
        updateParam.setWordCount(wordCount);
        updateParam.setVectorCount(0);
        updateParam.setContentPreview(preview);
        updateParam.setErrorMessage(StringUtils.defaultIfBlank(errorMessage, "Unknown error"));
        knowledgeDocumentService.update(updateParam);
    }

    private String buildPreview(List<String> sentenceList) {
        String preview = String.join("\n", sentenceList);
        if (preview.length() <= PREVIEW_LIMIT) {
            return preview;
        }
        return preview.substring(0, PREVIEW_LIMIT);
    }

    private KnowledgeDocument queryOwnedDocument(Long id) {
        KnowledgeDocument document = knowledgeDocumentService.queryExistent(id).getData();
        if (!document.getUserId().equals(ContextUtils.getUserId())) {
            throw new DataNotFoundException();
        }
        return document;
    }

    private ActionResult deleteRemoteVectors(KnowledgeDocument document) {
        return gatewayClientService.knowledgeVectorDelete(
            buildKnowledgeRequest(document.getId(), document.getUserId(), null, null)
        );
    }

    private void saveLocalFile(MultipartFile file, String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        Files.createDirectories(path.getParent());
        file.transferTo(path);
    }

    private void deleteLocalFile(String storagePath) {
        if (StringUtils.isBlank(storagePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete knowledge source file", e);
        }
    }

    private String buildStoragePath(Long userId, Long documentId, String fileType) {
        return ConfigUtils.CONFIG_BASE_PATH
            + File.separator
            + KNOWLEDGE_DIRECTORY
            + File.separator
            + userId
            + File.separator
            + documentId
            + "."
            + ParserFactory.normalizeFileType(fileType);
    }

    private String stripExtension(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    private String getFileType(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return "";
        }
        return ParserFactory.normalizeFileType(fileName.substring(fileName.lastIndexOf('.') + 1));
    }

    private String buildUnsupportedFileTypeMessage(String fileName, String fileType) {
        String displayName = StringUtils.defaultIfBlank(fileName, "current file");
        String displayType = StringUtils.defaultIfBlank(fileType, "unknown");
        return String.format(
            "Unsupported file type for %s (%s). Supported formats: %s.",
            displayName,
            displayType,
            SUPPORTED_FORMATS
        );
    }

    private String buildNoExtractableTextMessage(String fileType) {
        String normalizedType = ParserFactory.normalizeFileType(fileType);
        return switch (normalizedType) {
            case "pdf" -> "No extractable text was found in the PDF. The file may be image-only, encrypted, malformed, or still require OCR.";
            case "doc", "docx" -> "No extractable text was found in the Word document. The file may be empty, protected, or malformed.";
            case "xls", "xlsx", "csv" -> "No extractable text was found in the spreadsheet. The file may be empty, protected, or contain only unsupported cell content.";
            default -> "No extractable text was found in the uploaded document. The file may be empty, malformed, or encoded in an unsupported way.";
        };
    }

    private String buildEmbeddingUnsupportedMessage() {
        return String.format(
            "Current AI source %s does not support knowledge-base embeddings. Please switch to CHAT2DBAI, TONGYIQIANWENAI, or FASTCHATAI.",
            aiEmbeddingService.currentProviderName()
        );
    }

    private String buildNoEmbeddingVectorMessage(String fileType) {
        String normalizedType = StringUtils.defaultIfBlank(ParserFactory.normalizeFileType(fileType), "document");
        return String.format(
            "Embedding generation returned no vectors for the extracted %s text. Please check the current AI provider configuration and API credentials.",
            normalizedType
        );
    }
}
