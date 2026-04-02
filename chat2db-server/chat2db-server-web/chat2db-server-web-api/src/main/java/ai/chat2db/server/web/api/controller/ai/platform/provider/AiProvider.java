package ai.chat2db.server.web.api.controller.ai.platform.provider;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;

public interface AiProvider {

    AiSqlSourceEnum getSource();

    void streamChat(AiProviderRequest request, AiStreamCallback callback);

    default AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw new UnsupportedOperationException(getSource() + " does not support embeddings");
    }

    default boolean supportsEmbedding() {
        return false;
    }

    default boolean supportsConversationContext() {
        return true;
    }

    default void validatePrompt(String prompt) {
    }
}
