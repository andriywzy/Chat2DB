package ai.chat2db.server.web.api.controller.ai.platform.retrieval;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;

public interface ProjectSchemaContextService {

    AiRetrievalContext retrieveSchema(AiRetrievalQuery query);
}
