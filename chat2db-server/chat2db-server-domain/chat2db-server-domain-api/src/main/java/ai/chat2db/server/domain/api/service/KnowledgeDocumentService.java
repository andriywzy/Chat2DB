package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentCreateParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentPageQueryParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentUpdateParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;

public interface KnowledgeDocumentService {

    DataResult<Long> create(KnowledgeDocumentCreateParam param);

    ActionResult update(KnowledgeDocumentUpdateParam param);

    DataResult<KnowledgeDocument> queryExistent(Long id);

    PageResult<KnowledgeDocument> queryPage(KnowledgeDocumentPageQueryParam param);

    ActionResult deleteWithPermission(Long id);
}
