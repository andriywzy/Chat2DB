package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.chat2db.listener.Chat2dbAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.springframework.stereotype.Component;

@Component
public class Chat2dbAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.CHAT2DBAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        Chat2dbAIClient.getInstance().streamCompletions(
            toOpenAiMessages(request.getMessages()),
            new Chat2dbAIEventSourceListener(callback)
        );
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        return toEmbeddingResponse(Chat2dbAIClient.getInstance().embeddings(request.getInput()));
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
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
