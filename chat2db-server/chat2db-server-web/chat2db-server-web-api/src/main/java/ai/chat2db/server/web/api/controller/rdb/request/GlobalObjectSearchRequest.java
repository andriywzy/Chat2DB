package ai.chat2db.server.web.api.controller.rdb.request;

import ai.chat2db.server.tools.base.wrapper.request.PageQueryRequest;
import lombok.Data;

import java.util.List;

@Data
public class GlobalObjectSearchRequest extends PageQueryRequest {

    private String keyword;

    private List<String> types;

    private Boolean refresh = Boolean.FALSE;
}
