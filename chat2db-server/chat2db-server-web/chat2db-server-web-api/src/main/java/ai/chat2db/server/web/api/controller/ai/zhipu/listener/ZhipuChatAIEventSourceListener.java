package ai.chat2db.server.web.api.controller.ai.zhipu.listener;

import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.zhipu.model.ZhipuChatCompletions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * description：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class ZhipuChatAIEventSourceListener extends AbstractAiEventSourceListener {

    public ZhipuChatAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public ZhipuChatAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("Zhipu Chat Sse connecting...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, @NotNull String data) {
        log.info("Zhipu Chat AI response data：{}", data);
        if (data.equals("[DONE]")) {
            log.info("Zhipu Chat AI closed");
            complete();
            return;
        }

        ZhipuChatCompletions chatCompletions = mapper.readValue(data, ZhipuChatCompletions.class);
        String text = chatCompletions.getChoices().get(0).getDelta()==null?
                chatCompletions.getChoices().get(0).getText()
                :chatCompletions.getChoices().get(0).getDelta().getContent();

        sendChunk(chatCompletions.getId(), text);
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("Zhipu Chat AI closes sse connection closed");
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
            log.error("Zhipu Chat AI sse response：{}", bodyString);
            eventSource.cancel();
            fail("Zhipu Chat AI error：" + bodyString);
        } catch (Exception exception) {
            log.error("Zhipu Chat AI send data error:", exception);
        }
    }
}
