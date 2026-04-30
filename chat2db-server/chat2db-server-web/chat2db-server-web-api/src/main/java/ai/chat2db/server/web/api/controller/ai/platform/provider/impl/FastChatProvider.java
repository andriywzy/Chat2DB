package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatAIClient;
import ai.chat2db.server.web.api.controller.ai.fastchat.listener.FastChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.springframework.stereotype.Component;

@Component
public class FastChatProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.FASTCHATAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        FastChatAIClient.getInstance().streamCompletions(
            toFastChatMessages(request.getMessages()),
            new FastChatAIEventSourceListener(callback)
        );
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        return toEmbeddingResponse(FastChatAIClient.getInstance().embeddings(request.getInput()));
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
    }
}
