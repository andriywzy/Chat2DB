package ai.chat2db.server.web.api.controller.ai.platform.prompt;

import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;

import java.util.List;

public interface AiSchemaContextService {

    String queryDatabaseType(ChatQueryRequest queryRequest);

    List<String> buildSelectedTableSchemas(ChatQueryRequest queryRequest);

    AiRetrievalContext buildSelectedSchemaContext(ChatQueryRequest queryRequest);
}
