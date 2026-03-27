package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.request.ChatRequest;
import ai.chat2db.server.web.api.controller.ai.service.AiPromptService;
import ai.chat2db.server.web.api.controller.ai.service.AiProviderRouterService;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * description：
 *
 * @author https:www.unfbx.com
 * @date 2023-03-01
 */
@RestController
@ConnectionInfoAspect
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {

    /**
     * chat timeout
     */
    protected static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    @Autowired
    private AiProviderRouterService aiProviderRouterService;

    @Autowired
    private AiPromptService aiPromptService;

    /**
     * Custom model streaming output interface DEMO
     * <p>
     *     Note: For custom AI that uses its own local streaming output, the interface input and output must be consistent with this sample.
     * </p>
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @PostMapping("/custom/stream/chat")
    @CrossOrigin
    public SseEmitter customChat(@RequestBody ChatRequest queryRequest) throws IOException {
        SseEmitter emitter = new SseEmitter(CHAT_TIMEOUT);

        emitter.onCompletion(() -> log.info(LocalDateTime.now() + ", on completion"));
        emitter.onTimeout(() -> {
            log.info(LocalDateTime.now() + ", uid# on timeout");
            emitter.complete();
        });

        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    emitter.send(SseEmitter.event().name("message").data("Event " + i));
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    /**
     * Custom model non-streaming output interface DEMO
     * <p>
     *       Note: Use your own local flying flow output to customize the AI. The interface input and output must be consistent with this sample.
     * </p>
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @PostMapping("/custom/non/stream/chat")
    @CrossOrigin
    public String customNonStreamChat(@RequestBody ChatRequest queryRequest) {
        return "The custom AI sample interface is connected successfully! ! ! !";
    }

    /**
     * SQL conversion model
     *
     * @param queryRequest
     * @param headers
     * @return
     * @throws IOException
     */
    @GetMapping("/chat")
    @CrossOrigin
    public SseEmitter completions(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
        throws IOException {
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

    /**
     * distribute with different AI
     *
     * @return
     */
    public SseEmitter distributeAISql(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        return aiProviderRouterService.distributeAISql(queryRequest, sseEmitter, uid);
    }

    /**
     * query chat2db apikey
     *
     * @return
     */
    public String getApiKey() {
        return aiPromptService.getApiKey();
    }

    /**
     * query database type
     *
     * @param queryRequest
     * @return
     */
    public String queryDatabaseType(ChatQueryRequest queryRequest) {
        return aiPromptService.queryDatabaseType(queryRequest);
    }

    public String mappingDatabaseSchema(ChatQueryRequest queryRequest) {
        return aiPromptService.mappingDatabaseSchema(queryRequest);
    }

    /**
     * query database schema
     *
     * @param queryRequest
     * @return
     */
    public String queryDatabaseSchema(ChatQueryRequest queryRequest) {
        return aiPromptService.queryDatabaseSchema(queryRequest);
    }

    /**
     * query database schema
     *
     * @param queryRequest
     * @return
     */
    public String querySchemaByEs(ChatQueryRequest queryRequest) {
        return aiPromptService.querySchemaByEs(queryRequest);
    }

    /**
     * distribute embedding with different AI
     *
     * @return
     */
    public FastChatEmbeddingResponse distributeAIEmbedding(String input) {
        return aiPromptService.distributeAIEmbedding(input);
    }
}
