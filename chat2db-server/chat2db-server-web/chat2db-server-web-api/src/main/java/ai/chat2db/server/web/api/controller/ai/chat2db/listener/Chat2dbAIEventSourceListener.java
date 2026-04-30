package ai.chat2db.server.web.api.controller.ai.chat2db.listener;

import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.response.ChatCompletionResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * Description: Chat2dbAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class Chat2dbAIEventSourceListener extends AbstractAiEventSourceListener {

    public Chat2dbAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public Chat2dbAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("Chat2db AI 建立sse连接...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("Chat2db AI returns data: {}", data);
        if (data.equals("[DONE]")) {
            log.info("Chat2db AI return data is over");
            complete();
            return;
        }
        ChatCompletionResponse completionResponse = mapper.readValue(data, ChatCompletionResponse.class);
        String text = completionResponse.getChoices().get(0).getDelta() == null
                ? completionResponse.getChoices().get(0).getText()
                : completionResponse.getChoices().get(0).getDelta().getContent();
        String completionId = completionResponse.getId();
        if (text != null) {
            sendChunk(completionId, text);
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("Chat2db AI closes sse connection...");
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
            log.error("Chat2db AI sse connection exception data: {}", bodyString, t);
            eventSource.cancel();
            fail("Chat2db AI Error：" + bodyString);
        } catch (Exception exception) {
            log.error("Exception in sending data:", exception);
        }
    }
}
