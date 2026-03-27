package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.request.KnowledgeDocumentQueryRequest;
import ai.chat2db.server.web.api.controller.ai.service.KnowledgeDocumentAppService;
import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentPageQueryParam;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.model.Knowledge;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author moji
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai/knowledge")
@Slf4j
public class KnowledgeController extends ChatController {


    /**
     * chat timeout
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();


    @Resource
    private GatewayClientService gatewayClientService;

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private KnowledgeDocumentAppService knowledgeDocumentAppService;

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
        return knowledgeDocumentService.deleteWithPermission(id);
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
    public SseEmitter search(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
            throws Exception {
        // request embedding
        FastChatEmbeddingResponse response = distributeAIEmbedding(queryRequest.getMessage());
        List<List<BigDecimal>> contentVector = new ArrayList<>();
        contentVector.add(response.getData().get(0).getEmbedding());

        // search embedding
        KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
        knowledgeRequest.setContentVector(contentVector);
        DataResult<KnowledgeResponse> result = gatewayClientService.knowledgeVectorSearch(knowledgeRequest);
        queryRequest.setPromptType(PromptType.TEXT_GENERATION.getCode());
        String prompt = queryRequest.getMessage();
        if (CollectionUtils.isNotEmpty(result.getData().getKnowledgeList())) {
            List<String> contents = new ArrayList<>();
            for(Knowledge data: result.getData().getKnowledgeList()){
                contents.add(data.getContent());
            }

            prompt = String.format("Based on %s. Please answer %s.", JSON.toJSONString(contents), prompt);
            queryRequest.setMessage(prompt);
        }

        // chat with AI
        SseEmitter sseEmitter = new SseEmitter(CHAT_TIMEOUT);
        String uid = headers.get("uid");
        if (StrUtil.isBlank(uid)) {
            throw new ParamBusinessException("uid");
        }

        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        return distributeAISql(queryRequest, sseEmitter, uid);
    }

}
