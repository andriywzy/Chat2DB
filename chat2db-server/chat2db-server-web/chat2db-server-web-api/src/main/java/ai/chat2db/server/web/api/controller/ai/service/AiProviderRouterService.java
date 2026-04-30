package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.orchestrator.AiOrchestrator;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class AiProviderRouterService {

    @Autowired
    private AiOrchestrator aiOrchestrator;

    public SseEmitter distributeAISql(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        return aiOrchestrator.streamChat(AiChatCommand.builder()
            .queryRequest(queryRequest)
            .emitter(sseEmitter)
            .uid(uid)
            .build());
    }
}
