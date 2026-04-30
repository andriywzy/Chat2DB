package ai.chat2db.server.web.api.controller.ai.platform.config;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.rest.client.RestAIClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class DefaultAiConfigResolver implements AiConfigResolver {

    private final ConfigService configService;

    @Value("${chatgpt.context.length}")
    private Integer contextLength;

    public DefaultAiConfigResolver(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public AiSqlSourceEnum getCurrentAiSqlSource() {
        Config config = configService.find(RestAIClient.AI_SQL_SOURCE).getData();
        String aiSqlSource = AiSqlSourceEnum.CHAT2DBAI.getCode();
        if (Objects.nonNull(config) && StringUtils.isNotBlank(config.getContent())) {
            aiSqlSource = config.getContent();
        }
        AiSqlSourceEnum aiSqlSourceEnum = AiSqlSourceEnum.getByName(aiSqlSource);
        if (Objects.isNull(aiSqlSourceEnum)) {
            return AiSqlSourceEnum.OPENAI;
        }
        return aiSqlSourceEnum;
    }

    @Override
    public String buildConversationKey(String uid) {
        return getCurrentAiSqlSource().getCode() + uid;
    }

    @Override
    public String getChat2dbApiKey() {
        if (!AiSqlSourceEnum.CHAT2DBAI.equals(getCurrentAiSqlSource())) {
            return null;
        }
        Config keyConfig = configService.find(Chat2dbAIClient.CHAT2DB_OPENAI_KEY).getData();
        if (Objects.isNull(keyConfig) || StringUtils.isBlank(keyConfig.getContent())) {
            return null;
        }
        return keyConfig.getContent();
    }

    @Override
    public Integer getContextLength() {
        return contextLength;
    }
}
