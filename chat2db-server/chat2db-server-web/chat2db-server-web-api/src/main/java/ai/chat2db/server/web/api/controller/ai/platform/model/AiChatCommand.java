package ai.chat2db.server.web.api.controller.ai.platform.model;

import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatCommand {

    private ChatQueryRequest queryRequest;

    private String uid;

    private SseEmitter emitter;

    private String promptOverride;

    private AiRetrievalContext retrievalContext;
}
