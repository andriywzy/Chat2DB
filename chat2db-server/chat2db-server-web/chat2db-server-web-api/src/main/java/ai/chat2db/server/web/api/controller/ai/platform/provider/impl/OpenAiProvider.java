package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.openai.client.OpenAIClient;
import ai.chat2db.server.web.api.controller.ai.openai.listener.OpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.OPENAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        OpenAIClient.getInstance().streamChatCompletion(
            toOpenAiMessages(request.getMessages()),
            new OpenAIEventSourceListener(callback)
        );
    }

    @Override
    public boolean supportsConversationContext() {
        return false;
    }

    @Override
    public void validatePrompt(String prompt) {
        validatePromptLength(prompt);
    }
}
