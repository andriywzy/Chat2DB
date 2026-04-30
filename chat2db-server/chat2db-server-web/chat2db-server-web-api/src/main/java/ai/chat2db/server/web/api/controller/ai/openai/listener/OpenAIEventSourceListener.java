package ai.chat2db.server.web.api.controller.ai.openai.listener;

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
 * description：OpenAIEventSourceListener
 *
 * @author https:www.unfbx.com
 * @date 2023-02-22
 */
@Slf4j
public class OpenAIEventSourceListener extends AbstractAiEventSourceListener {

    public OpenAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public OpenAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("OpenAI建立sse连接...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("OpenAI returns data: {}", data);
        if (data.equals("[DONE]")) {
            log.info("OpenAI returns data ended");
            complete();
            return;
        }
        ChatCompletionResponse completionResponse = mapper.readValue(data, ChatCompletionResponse.class);
        String text = completionResponse.getChoices().get(0).getDelta() == null
            ? completionResponse.getChoices().get(0).getText()
            : completionResponse.getChoices().get(0).getDelta().getContent();
        if (text != null) {
            sendChunk(completionResponse.getId(), text);
        }
    }

    @Override
    public void onClosed(EventSource eventSource) {
        log.info("OpenAI closes sse connection...");
        super.onClosed(eventSource);
    }

    @Override
    public void onFailure(EventSource eventSource, Throwable t, Response response) {
        try {
            if (Objects.isNull(response)) {
                String message = t.getMessage();
                if ("No route to host".equals(message)) {
                    message = "The network connection timed out. Please Baidu solve the network problem by yourself.";
                }
                fail(message);
                return;
            }
            String bodyString = resolveFailureBody(t, response);
            log.error("OpenAI sse connection exception data: {}", bodyString, t);
            eventSource.cancel();
            fail("An exception occurred, please view the detailed log in the help：" + bodyString);
        } catch (Exception exception) {
            log.error("Exception in sending data:", exception);
        }
    }
}
