package ai.chat2db.server.web.api.controller.ai.azure.listener;

import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatChoice;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatCompletions;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureCompletionsUsage;
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
public class AzureOpenAIEventSourceListener extends AbstractAiEventSourceListener {

    public AzureOpenAIEventSourceListener(SseEmitter sseEmitter) {
        this(new SseEmitterAiStreamCallback(sseEmitter));
    }

    public AzureOpenAIEventSourceListener(AiStreamCallback callback) {
        super(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onOpen(EventSource eventSource, Response response) {
        log.info("AzureOpenAI建立sse连接...");
    }

    /**
     * {@inheritDoc}
     */
    @SneakyThrows
    @Override
    public void onEvent(EventSource eventSource, String id, String type, String data) {
        log.info("AzureOpenAI returns data: {}", data);
        if (data.equals("[DONE]")) {
            log.info("AzureOpenAI returns data ended");
            complete();
            return;
        }

        AzureChatCompletions chatCompletions = mapper.readValue(data, AzureChatCompletions.class);
        String text = "";
        log.info("Model ID={} is created at {}.", chatCompletions.getId(),
            chatCompletions.getCreated());
        for (AzureChatChoice choice : chatCompletions.getChoices()) {
            AzureChatMessage message = choice.getDelta();
            if (message != null) {
                log.info("Index: {}, Chat Role: {}", choice.getIndex(), message.getRole());
                if (message.getContent() != null) {
                    text = message.getContent();
                }
            }
        }

        AzureCompletionsUsage usage = chatCompletions.getUsage();
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
        log.info("AzureOpenAI close sse connection...");
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
            log.error("Azure OpenAI sse response：{}", bodyString);
            eventSource.cancel();
            fail("Azure OpenAI error：" + bodyString);
        } catch (Exception exception) {
            log.error("Azure OpenAI sends data abnormally:", exception);
        }
    }
}
