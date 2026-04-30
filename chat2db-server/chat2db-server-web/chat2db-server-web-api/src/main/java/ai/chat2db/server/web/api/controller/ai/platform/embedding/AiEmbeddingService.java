package ai.chat2db.server.web.api.controller.ai.platform.embedding;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;

public interface AiEmbeddingService {

    AiEmbeddingResponse embed(String input);

    boolean supportsCurrentProvider();

    String currentProviderName();
}
