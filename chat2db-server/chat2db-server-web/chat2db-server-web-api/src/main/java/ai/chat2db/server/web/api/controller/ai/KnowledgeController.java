package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.orchestrator.AiOrchestrator;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiPromptBuilder;
import ai.chat2db.server.web.api.controller.ai.platform.retrieval.AiRetrievalService;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.request.KnowledgeDocumentQueryRequest;
import ai.chat2db.server.web.api.controller.ai.service.KnowledgeDocumentAppService;
import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentPageQueryParam;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @author moji
 */
@RestController
@RequestMapping("/api/ai/knowledge")
@Slf4j
public class KnowledgeController extends ChatController {


    /**
     * chat timeout
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private KnowledgeDocumentAppService knowledgeDocumentAppService;

    @Resource
    private AiRetrievalService aiRetrievalService;

    @Resource
    private AiPromptBuilder aiPromptBuilder;

    @Resource
    private AiOrchestrator aiOrchestrator;

    /**
     * save knowledge from pdf file
     *
     * @param file
     * @return
     * @throws IOException
     */
    @PostMapping("/embeddings")
    @CrossOrigin
    public ActionResult embeddings(MultipartFile file, HttpServletRequest request)
        throws Exception {
        DataResult<KnowledgeDocument> result = knowledgeDocumentAppService.upload(null, file);
        if (!result.success()) {
            return ActionResult.fail(result.getErrorCode(), result.getErrorMessage(), result.getErrorDetail());
        }
        return ActionResult.isSuccess();
    }

    @PostMapping("/document/upload")
    @CrossOrigin
    public DataResult<KnowledgeDocument> uploadDocument(
        @RequestParam(value = "name", required = false) String name,
        @RequestParam("file") MultipartFile file
    ) throws Exception {
        return knowledgeDocumentAppService.upload(name, file);
    }

    @GetMapping("/document/list")
    public WebPageResult<KnowledgeDocument> listDocument(KnowledgeDocumentQueryRequest request) {
        KnowledgeDocumentPageQueryParam param = new KnowledgeDocumentPageQueryParam();
        param.setPageNo(request.getPageNo());
        param.setPageSize(request.getPageSize());
        param.setSearchKey(request.getSearchKey());
        param.setStatus(request.getStatus());
        param.setUserId(ContextUtils.getUserId());
        PageResult<KnowledgeDocument> result = knowledgeDocumentService.queryPage(param);
        return WebPageResult.of(result.getData(), result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    @GetMapping("/document/{id}")
    public DataResult<KnowledgeDocument> getDocument(@PathVariable Long id) {
        KnowledgeDocument document = knowledgeDocumentService.queryExistent(id).getData();
        if (!document.getUserId().equals(ContextUtils.getUserId())) {
            throw new ParamBusinessException("id");
        }
        return DataResult.of(document);
    }

    @DeleteMapping("/document/{id}")
    public ActionResult deleteDocument(@PathVariable Long id) {
        return knowledgeDocumentAppService.delete(id);
    }

    @PostMapping("/document/{id}/rebuild")
    public DataResult<KnowledgeDocument> rebuildDocument(@PathVariable Long id) throws Exception {
        return knowledgeDocumentAppService.rebuild(id);
    }

    /**
     * search knowledge
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @GetMapping("/search")
    @CrossOrigin
    public SseEmitter search(
        ChatQueryRequest queryRequest,
        @RequestParam(value = "documentIds", required = false) List<Long> documentIds,
        @RequestHeader Map<String, String> headers
    )
            throws Exception {
        String uid = resolveUid(queryRequest, headers);

        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        AiRetrievalContext retrievalContext = buildKnowledgeRetrievalContext(queryRequest.getMessage(), documentIds);
        String prompt = aiPromptBuilder.buildKnowledgePrompt(queryRequest.getMessage(), retrievalContext);
        queryRequest.setPromptType(PromptType.TEXT_GENERATION.getCode());
        return aiOrchestrator.streamChat(AiChatCommand.builder()
            .queryRequest(queryRequest)
            .uid(uid)
            .promptOverride(prompt)
            .retrievalContext(retrievalContext)
            .emitter(new SseEmitter(CHAT_TIMEOUT))
            .build());
    }

    @GetMapping("/search_context")
    public DataResult<KnowledgeResponse> searchContext(
        ChatQueryRequest queryRequest,
        @RequestParam(value = "documentIds", required = false) List<Long> documentIds
    ) {
        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }
        AiRetrievalContext retrievalContext = buildKnowledgeRetrievalContext(queryRequest.getMessage(), documentIds);
        return DataResult.of(new KnowledgeResponse(retrievalContext.getKnowledgeSources()));
    }

    private AiRetrievalContext buildKnowledgeRetrievalContext(String message, List<Long> documentIds) {
        try {
            return aiRetrievalService.retrieveKnowledge(AiRetrievalQuery.builder()
                .userId(ContextUtils.getUserId())
                .message(message)
                .documentIds(CollectionUtils.isEmpty(documentIds) ? List.of() : documentIds)
                .build());
        } catch (BusinessException exception) {
            throw exception;
        }
    }
}
