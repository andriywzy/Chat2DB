package ai.chat2db.server.web.api.controller.ai.platform.context;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

public interface AiContextService {

    SseEmitter initializeEmitter(SseEmitter emitter, String conversationKey) throws IOException;

    List<AiChatMessage> buildMessages(String conversationKey, String prompt, Integer contextLength, boolean withHistory);

    void saveMessages(String conversationKey, List<AiChatMessage> messages);

    String buildConversationKey(AiSqlSourceEnum source, String uid);
}
