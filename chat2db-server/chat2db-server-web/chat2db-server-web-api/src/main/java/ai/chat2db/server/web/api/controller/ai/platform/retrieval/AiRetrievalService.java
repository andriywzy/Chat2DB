package ai.chat2db.server.web.api.controller.ai.platform.retrieval;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;

public interface AiRetrievalService {

    AiRetrievalContext retrieveSchema(AiRetrievalQuery query);

    AiRetrievalContext retrieveKnowledge(AiRetrievalQuery query);
}
