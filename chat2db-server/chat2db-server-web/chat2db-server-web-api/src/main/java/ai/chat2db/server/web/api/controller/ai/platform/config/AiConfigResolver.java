package ai.chat2db.server.web.api.controller.ai.platform.config;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;

public interface AiConfigResolver {

    AiSqlSourceEnum getCurrentAiSqlSource();

    String buildConversationKey(String uid);

    String getChat2dbApiKey();

    Integer getContextLength();
}
