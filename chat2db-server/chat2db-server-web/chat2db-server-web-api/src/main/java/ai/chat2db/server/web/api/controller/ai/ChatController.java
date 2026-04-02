package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.orchestrator.AiOrchestrator;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.request.ChatRequest;
import ai.chat2db.server.tools.common.util.ContextUtils;
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
@RequestMapping("/api/ai")
@Slf4j
public class ChatController {

    /**
     * chat timeout
     */
    protected static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    @Autowired
    private AiOrchestrator aiOrchestrator;

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
        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        return aiOrchestrator.streamChat(AiChatCommand.builder()
            .queryRequest(queryRequest)
            .uid(resolveUid(queryRequest, headers))
            .emitter(new SseEmitter(CHAT_TIMEOUT))
            .build());
    }

    protected String resolveUid(ChatQueryRequest queryRequest, Map<String, String> headers) {
        String uid = headers.get("uid");
        if (StrUtil.isBlank(uid)) {
            uid = queryRequest.getUid();
        }
        if (StrUtil.isBlank(uid) && ContextUtils.getLoginUser() != null) {
            uid = String.valueOf(ContextUtils.getLoginUser().getId());
        }
        if (StrUtil.isBlank(uid)) {
            throw new ParamBusinessException("uid");
        }
        return uid;
    }
}
