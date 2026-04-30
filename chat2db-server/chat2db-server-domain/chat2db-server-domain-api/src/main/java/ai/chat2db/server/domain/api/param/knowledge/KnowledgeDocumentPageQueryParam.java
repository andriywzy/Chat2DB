package ai.chat2db.server.domain.api.param.knowledge;

import ai.chat2db.server.tools.base.wrapper.param.PageQueryParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeDocumentPageQueryParam extends PageQueryParam {

    private Long userId;

    private String searchKey;

    private String status;
}
