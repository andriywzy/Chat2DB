package ai.chat2db.server.web.api.controller.ai.claude.listener;

import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeCompletionResponse;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * ClaudeAIEventSourceListener
 */
@Slf4j
public class ClaudeAIEventSourceListener extends AbstractAiEventSourceListener {

    public ClaudeAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public ClaudeAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("ClaudeAIEventSourceListener...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("Claude AI data：{}", data);
        if (data.equals("[DONE]")) {
            log.info("Claude AI end");
            complete();
            return;
        }
        ClaudeCompletionResponse completionResponse = mapper.readValue(data, ClaudeCompletionResponse.class);
        String text = completionResponse.getCompletion();
        if (text != null) {
            sendChunk(null, text);
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("Claude AI closed...");
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
            log.error("Claude sse error：{}", bodyString, t);
            eventSource.cancel();
            fail("Claude sse error：" + bodyString);
        } catch (Exception exception) {
            log.error("Exception in sending data:", exception);
        }
    }
}
