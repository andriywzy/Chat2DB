package ai.chat2db.server.web.api.controller.ai.request;

import ai.chat2db.server.tools.base.wrapper.request.PageQueryRequest;
import lombok.Data;

@Data
public class KnowledgeDocumentQueryRequest extends PageQueryRequest {

    private String searchKey;

    private String status;
}
