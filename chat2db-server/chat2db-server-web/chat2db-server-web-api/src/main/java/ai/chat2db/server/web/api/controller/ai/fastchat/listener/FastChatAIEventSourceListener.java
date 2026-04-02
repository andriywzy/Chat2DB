package ai.chat2db.server.web.api.controller.ai.fastchat.listener;

import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatChoice;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatCompletions;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatCompletionsUsage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
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
public class FastChatAIEventSourceListener extends AbstractAiEventSourceListener {

    public FastChatAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public FastChatAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("Fast Chat Sse connecting...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("Fast Chat AI response data：{}", data);
        if (data.equals("[DONE]")) {
            log.info("Fast Chat AI closed");
            complete();
            return;
        }

        FastChatCompletions chatCompletions = mapper.readValue(data, FastChatCompletions.class);
        String text = "";
        log.info("Model={} is created at {}.", chatCompletions.getId(),
            chatCompletions.getCreated());
        for (FastChatChoice choice : chatCompletions.getChoices()) {
            FastChatMessage message = choice.getDelta();
            if (message != null) {
                log.info("Index: {}, Chat Role: {}", choice.getIndex(), message.getRole());
                if (message.getContent() != null) {
                    text = message.getContent();
                }
            }
        }

        FastChatCompletionsUsage usage = chatCompletions.getUsage();
        if (usage != null) {
            log.info(
                "Usage: number of prompt token is {}, number of completion token is {}, and number of total "
                    + "tokens in request and response is {}.%n", usage.getPromptTokens(),
                usage.getCompletionTokens(), usage.getTotalTokens());
        }

        sendChunk(chatCompletions.getId(), text);
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
            log.error("Fast Chat AI sse response：{}", bodyString);
            eventSource.cancel();
            fail("Fast Chat AI error：" + bodyString);
        } catch (Exception exception) {
            log.error("Fast Chat AI send data error:", exception);
        }
    }
}
