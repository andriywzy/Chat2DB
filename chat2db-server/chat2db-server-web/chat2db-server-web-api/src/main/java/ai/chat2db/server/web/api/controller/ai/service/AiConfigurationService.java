package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.config.AiConfigResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiConfigurationService {

    @Autowired
    private AiConfigResolver aiConfigResolver;

    public AiSqlSourceEnum getCurrentAiSqlSource() {
        return aiConfigResolver.getCurrentAiSqlSource();
    }

    public String buildScopedUid(String uid) {
        return aiConfigResolver.buildConversationKey(uid);
    }

    public String getChat2dbApiKey() {
        return aiConfigResolver.getChat2dbApiKey();
    }
}
