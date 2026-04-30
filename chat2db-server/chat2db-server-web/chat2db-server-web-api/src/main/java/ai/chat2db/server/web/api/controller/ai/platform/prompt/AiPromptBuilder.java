package ai.chat2db.server.web.api.controller.ai.platform.prompt;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;

public interface AiPromptBuilder {

    String buildPrompt(ChatQueryRequest queryRequest);

    String buildPrompt(ChatQueryRequest queryRequest, AiRetrievalContext retrievalContext);

    String buildKnowledgePrompt(String question, AiRetrievalContext retrievalContext);
}
