package ai.chat2db.server.web.api.controller.ai.platform.provider;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatRole;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import com.unfbx.chatgpt.entity.chat.Message;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractAiProvider implements AiProvider {

    private static final Integer MAX_PROMPT_LENGTH = 3850;
    private static final Integer TOKEN_CONVERT_CHAR_LENGTH = 4;

    protected List<Message> toOpenAiMessages(List<AiChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            if (message == null || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            Message.Role role = Message.Role.USER;
            if ("assistant".equalsIgnoreCase(message.getRole())) {
                role = Message.Role.ASSISTANT;
            } else if ("system".equalsIgnoreCase(message.getRole())) {
                role = Message.Role.SYSTEM;
            }
            result.add(Message.builder().role(role).content(message.getContent()).build());
        }
        return result;
    }

    protected List<FastChatMessage> toFastChatMessages(List<AiChatMessage> messages) {
        List<FastChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            if (message == null || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            FastChatRole role = FastChatRole.fromString(StringUtils.defaultIfBlank(message.getRole(), "user"));
            result.add(new FastChatMessage(role).setContent(message.getContent()));
        }
        return result;
    }

    protected List<AzureChatMessage> toAzureMessages(List<AiChatMessage> messages) {
        List<AzureChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            if (message == null || StringUtils.isBlank(message.getContent())) {
                continue;
            }
            AzureChatRole role = AzureChatRole.fromString(StringUtils.defaultIfBlank(message.getRole(), "user"));
            result.add(new AzureChatMessage(role).setContent(message.getContent()));
        }
        return result;
    }

    protected AiEmbeddingResponse toEmbeddingResponse(FastChatEmbeddingResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getData())) {
            return null;
        }
        return AiEmbeddingResponse.builder()
            .vectors(response.getData().stream().map(item -> item.getEmbedding()).toList())
            .build();
    }

    protected void validatePromptLength(String prompt) {
        if (prompt.length() / TOKEN_CONVERT_CHAR_LENGTH > MAX_PROMPT_LENGTH) {
            throw new ParamBusinessException();
        }
    }
}
