package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.domain.api.enums.KnowledgeDocumentStatusEnum;
import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentCreateParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentUpdateParam;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.DocParser.AbstractParser;
import ai.chat2db.server.web.api.controller.ai.DocParser.PdfParse;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeDocumentAppService {

    private static final Integer PREVIEW_LIMIT = 8000;

    @Autowired
    private AiPromptService aiPromptService;

    @Autowired
    private GatewayClientService gatewayClientService;

    @Autowired
    private KnowledgeDocumentService knowledgeDocumentService;

    public DataResult<KnowledgeDocument> upload(String name, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ParamBusinessException("file");
        }
        if (!StringUtils.endsWithIgnoreCase(file.getOriginalFilename(), ".pdf")) {
            throw new ParamBusinessException("file");
        }

        String fileName = file.getOriginalFilename();
        String documentName = StringUtils.isNotBlank(name) ? name : stripExtension(fileName);
        Long documentId = knowledgeDocumentService.create(buildCreateParam(documentName, fileName)).getData();

        List<String> sentenceList = new ArrayList<>();
        String preview = null;
        Integer wordCount = 0;
        Integer vectorCount = 0;

        try {
            AbstractParser parser = new PdfParse();
            sentenceList = parser.parse(file.getInputStream()).stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .toList();
            preview = buildPreview(sentenceList);
            wordCount = sentenceList.stream().mapToInt(String::length).sum();

            List<List<BigDecimal>> contentVector = new ArrayList<>();
            for (String sentence : sentenceList) {
                FastChatEmbeddingResponse response = aiPromptService.distributeAIEmbedding(sentence);
                if (response == null || response.getData() == null || response.getData().isEmpty()) {
                    continue;
                }
                contentVector.add(response.getData().get(0).getEmbedding());
            }
            vectorCount = contentVector.size();

            if (CollectionUtils.isEmpty(contentVector)) {
                updateAsFailed(documentId, sentenceList.size(), wordCount, preview, "No embedding vector generated");
                return DataResult.error("knowledge.document.upload.failed", "No embedding vector generated");
            }

            KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
            knowledgeRequest.setSentenceList(sentenceList);
            knowledgeRequest.setContentVector(contentVector);
            ActionResult result = gatewayClientService.knowledgeVectorSave(knowledgeRequest);
            if (!result.success()) {
                updateAsFailed(documentId, sentenceList.size(), wordCount, preview, result.getErrorMessage());
                return DataResult.error(
                    StringUtils.defaultIfBlank(result.getErrorCode(), "knowledge.document.upload.failed"),
                    StringUtils.defaultIfBlank(result.getErrorMessage(), "Knowledge upload failed")
                );
            }

            KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
            updateParam.setId(documentId);
            updateParam.setStatus(KnowledgeDocumentStatusEnum.READY.name());
            updateParam.setSentenceCount(sentenceList.size());
            updateParam.setWordCount(wordCount);
            updateParam.setVectorCount(vectorCount);
            updateParam.setContentPreview(preview);
            updateParam.setErrorMessage(null);
            knowledgeDocumentService.update(updateParam);
            return knowledgeDocumentService.queryExistent(documentId);
        } catch (Exception e) {
            updateAsFailed(documentId, sentenceList.size(), wordCount, preview, e.getMessage());
            throw e;
        }
    }

    private KnowledgeDocumentCreateParam buildCreateParam(String documentName, String fileName) {
        KnowledgeDocumentCreateParam param = new KnowledgeDocumentCreateParam();
        param.setName(documentName);
        param.setFileName(fileName);
        param.setFileType("pdf");
        param.setStatus(KnowledgeDocumentStatusEnum.PROCESSING.name());
        param.setSentenceCount(0);
        param.setWordCount(0);
        param.setVectorCount(0);
        return param;
    }

    private void updateAsFailed(Long documentId, Integer sentenceCount, Integer wordCount, String preview, String errorMessage) {
        KnowledgeDocumentUpdateParam updateParam = new KnowledgeDocumentUpdateParam();
        updateParam.setId(documentId);
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

    private String stripExtension(String fileName) {
        if (StringUtils.isBlank(fileName) || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }
}
