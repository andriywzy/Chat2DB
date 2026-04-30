package ai.chat2db.server.web.api.controller.ai.rest.listener;

import ai.chat2db.server.web.api.controller.ai.platform.callback.AbstractAiEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.rest.model.RestAIChatCompletions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;

/**
 * description：RESTAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class RestAIEventSourceListener extends AbstractAiEventSourceListener {

    public RestAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public RestAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("REST AI建立sse连接...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("REST AI return data:{}", data);
        String end = "[DONE]";
        if (data.equals(end)) {
            log.info("REST AI returns data finished");
            complete();
            return;
        }
        if (StringUtils.isNotBlank(data)) {
            RestAIChatCompletions chatCompletions = mapper.readValue(data, RestAIChatCompletions.class);
            String text = chatCompletions.getChoices().get(0).getDelta()==null?
                    chatCompletions.getChoices().get(0).getText()
                    :chatCompletions.getChoices().get(0).getDelta().getContent();
            sendChunk(id, text);
        }
    }

    @SneakyThrows
    @Override
    public void onClosed(EventSource eventSource) {
        log.info("REST AI close sse connection...");
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
            log.error("REST AI sse body error：{}，exception：{}", bodyString, t);
            if (Objects.nonNull(eventSource)) {
                eventSource.cancel();
            }
            fail("Rest AI Error:" + bodyString);
        } catch (Exception exception) {
            log.error("Exception in sending data:", exception);
        }
    }
}
