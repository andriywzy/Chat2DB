package ai.chat2db.server.web.api.controller.ai.platform.embedding;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.config.AiConfigResolver;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProvider;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProviderRegistry;
import org.springframework.stereotype.Service;

@Service
public class DefaultAiEmbeddingService implements AiEmbeddingService {

    private final AiConfigResolver aiConfigResolver;
    private final AiProviderRegistry providerRegistry;

    public DefaultAiEmbeddingService(AiConfigResolver aiConfigResolver, AiProviderRegistry providerRegistry) {
        this.aiConfigResolver = aiConfigResolver;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public AiEmbeddingResponse embed(String input) {
        AiSqlSourceEnum source = aiConfigResolver.getCurrentAiSqlSource();
        AiProvider provider = providerRegistry.get(source);
        if (!provider.supportsEmbedding()) {
            return null;
        }
        return provider.embed(AiEmbeddingRequest.builder().input(input).build());
    }

    @Override
    public boolean supportsCurrentProvider() {
        AiSqlSourceEnum source = aiConfigResolver.getCurrentAiSqlSource();
        AiProvider provider = providerRegistry.get(source);
        return provider.supportsEmbedding();
    }

    @Override
    public String currentProviderName() {
        AiSqlSourceEnum source = aiConfigResolver.getCurrentAiSqlSource();
        return source == null ? "UNKNOWN" : source.name();
    }
}
