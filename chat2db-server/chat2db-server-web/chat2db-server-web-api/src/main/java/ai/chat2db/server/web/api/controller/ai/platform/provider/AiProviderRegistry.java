package ai.chat2db.server.web.api.controller.ai.platform.provider;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderRegistry {

    private final Map<AiSqlSourceEnum, AiProvider> providerMap = new EnumMap<>(AiSqlSourceEnum.class);

    public AiProviderRegistry(List<AiProvider> providers) {
        for (AiProvider provider : providers) {
            providerMap.put(provider.getSource(), provider);
        }
    }

    public AiProvider get(AiSqlSourceEnum source) {
        AiProvider provider = providerMap.get(source);
        if (provider == null) {
            provider = providerMap.get(AiSqlSourceEnum.OPENAI);
        }
        if (provider == null) {
            throw new IllegalStateException("No AI provider registered for source " + source);
        }
        return provider;
    }
}
