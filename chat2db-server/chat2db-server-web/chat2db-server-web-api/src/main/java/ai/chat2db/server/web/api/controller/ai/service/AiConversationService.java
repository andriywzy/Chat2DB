package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatRole;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
import ai.chat2db.server.web.api.controller.ai.platform.context.AiContextService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiConversationService {

    private final AiContextService aiContextService;

    public AiConversationService(AiContextService aiContextService) {
        this.aiContextService = aiContextService;
    }

    public SseEmitter buildSseEmitter(SseEmitter sseEmitter, String uid) throws IOException {
        return aiContextService.initializeEmitter(sseEmitter, uid);
    }

    public List<FastChatMessage> buildFastChatMessages(String uid, String prompt, Integer contextLength) {
        List<AiChatMessage> messages = aiContextService.buildMessages(uid, prompt, contextLength, true);
        List<FastChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            result.add(new FastChatMessage(FastChatRole.fromString(message.getRole())).setContent(message.getContent()));
        }
        return result;
    }

    public List<AzureChatMessage> buildAzureMessages(String uid, String prompt, Integer contextLength) {
        List<AiChatMessage> messages = aiContextService.buildMessages(uid, prompt, contextLength, true);
        List<AzureChatMessage> result = new ArrayList<>();
        for (AiChatMessage message : messages) {
            result.add(new AzureChatMessage(AzureChatRole.fromString(message.getRole())).setContent(message.getContent()));
        }
        return result;
    }

    public void saveMessages(String uid, Object messages) {
        if (messages instanceof List<?> list) {
            List<AiChatMessage> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof AiChatMessage aiChatMessage) {
                    normalized.add(aiChatMessage);
                } else if (item instanceof FastChatMessage fastChatMessage) {
                    normalized.add(AiChatMessage.builder()
                        .role(fastChatMessage.getRole().toString())
                        .content(fastChatMessage.getContent())
                        .build());
                } else if (item instanceof AzureChatMessage azureChatMessage) {
                    normalized.add(AiChatMessage.builder()
                        .role(azureChatMessage.getRole().toString())
                        .content(azureChatMessage.getContent())
                        .build());
                }
            }
            aiContextService.saveMessages(uid, normalized);
        }
    }
}
