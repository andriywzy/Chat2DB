package ai.chat2db.server.web.api.controller.ai.baichuan.listener;

import ai.chat2db.server.web.api.controller.ai.baichuan.model.BaichuanChatCompletions;
import ai.chat2db.server.web.api.controller.ai.baichuan.model.BaichuanChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * description：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class BaichuanChatAIEventSourceListener extends AbstractAiEventSourceListener {

    public BaichuanChatAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public BaichuanChatAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("Baichuan Chat Sse connecting...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("Baichuan Chat AI response data：{}", data);
        if (data.equals("[DONE]")) {
            log.info("Baichuan Chat AI closed");
            complete();
            return;
        }

        BaichuanChatCompletions chatCompletions = mapper.readValue(data, BaichuanChatCompletions.class);
        String text = "";
        log.info("code={} msg={}", chatCompletions.getCode(), chatCompletions.getMsg());
        for (BaichuanChatMessage message : chatCompletions.getData().getMessages()) {
            if (message != null) {
                log.info("message: {}, Chat Role: {}", message.getContent(), message.getRole());
                if (message.getContent() != null) {
                    text = message.getContent();
                }
            }
        }

        sendChunk(chatCompletions.getMsg(), text);
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("FastChatAI close sse connection...");
        super.onClosed(eventSource);
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        try {
            if (Objects.isNull(response)) {
                fail(t.getMessage());
                return;
            }
            String bodyString = resolveFailureBody(t, response);
            if (StringUtils.isBlank(bodyString)) {
                bodyString = String.valueOf(response.code());
            }
            log.error("Baichuan Chat AI sse response：{}", bodyString);
            eventSource.cancel();
            fail("Baichuan Chat AI error：" + bodyString);
        } catch (Exception exception) {
            log.error("Baichuan Chat AI send data error:", exception);
        }
    }
}
