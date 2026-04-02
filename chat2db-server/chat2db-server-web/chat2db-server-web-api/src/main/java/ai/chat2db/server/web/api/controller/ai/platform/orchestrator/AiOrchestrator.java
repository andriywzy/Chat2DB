package ai.chat2db.server.web.api.controller.ai.platform.orchestrator;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public interface AiOrchestrator {

    SseEmitter streamChat(AiChatCommand command) throws IOException;
}
