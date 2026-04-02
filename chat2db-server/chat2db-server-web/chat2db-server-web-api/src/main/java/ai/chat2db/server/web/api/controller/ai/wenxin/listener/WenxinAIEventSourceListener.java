package ai.chat2db.server.web.api.controller.ai.wenxin.listener;

import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.wenxin.model.WenxinChatCompletions;
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
public class WenxinAIEventSourceListener extends AbstractAiEventSourceListener {

    public WenxinAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public WenxinAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("Wenxin chat Sse connecting...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("Wenxin AI response data：{}", data);
        if (data.equals("[DONE]")) {
            log.info("Wenxin AI closed");
            complete();
            return;
        }

        WenxinChatCompletions chatCompletions = mapper.readValue(data, WenxinChatCompletions.class);
        String text = chatCompletions.getResult();
        log.info("Model={} is created at {}. message:{}", chatCompletions.getObject(),
            chatCompletions.getCreated(), text);

        sendChunk(chatCompletions.getObject(), text);
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("WenxinChatAI close sse connection...");
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
            log.error("Wenxin chat AI sse response：{}", bodyString);
            eventSource.cancel();
            fail("Wenxin chat AI error：" + bodyString);
        } catch (Exception exception) {
            log.error("Wenxin chat AI send data error:", exception);
        }
    }
}
