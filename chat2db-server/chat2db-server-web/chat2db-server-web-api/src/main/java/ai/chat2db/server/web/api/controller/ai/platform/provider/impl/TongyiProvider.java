package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import ai.chat2db.server.web.api.controller.ai.tongyi.client.TongyiChatAIClient;
import ai.chat2db.server.web.api.controller.ai.tongyi.listener.TongyiChatAIEventSourceListener;
import org.springframework.stereotype.Component;

@Component
public class TongyiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.TONGYIQIANWENAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        TongyiChatAIClient.getInstance().streamCompletions(
            toFastChatMessages(request.getMessages()),
            new TongyiChatAIEventSourceListener(callback)
        );
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        return toEmbeddingResponse(TongyiChatAIClient.getInstance().embeddings(request.getInput()));
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
    }
}
