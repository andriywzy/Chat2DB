package ai.chat2db.gateway.start.controller;

import ai.chat2db.gateway.start.service.MilvusVectorService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import ai.chat2db.server.web.api.http.request.TableSchemaRequest;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import ai.chat2db.server.web.api.http.response.TableSchemaResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/milvus")
public class MilvusGatewayController {

    @Resource
    private MilvusVectorService milvusVectorService;

    @PostMapping("/knowledge/save")
    public ActionResult saveKnowledge(@RequestBody KnowledgeRequest request) {
        return milvusVectorService.saveKnowledge(request);
    }

    @PostMapping("/knowledge/search")
    public DataResult<KnowledgeResponse> searchKnowledge(@RequestBody KnowledgeRequest request) {
        return milvusVectorService.searchKnowledge(request);
    }

    @PostMapping("/knowledge/delete")
    public ActionResult deleteKnowledge(@RequestBody KnowledgeRequest request) {
        return milvusVectorService.deleteKnowledge(request);
    }

    @PostMapping("/schema/save")
    public ActionResult saveSchema(@RequestBody TableSchemaRequest request) {
        return milvusVectorService.saveSchema(request);
    }

    @PostMapping("/schema/search")
    public DataResult<TableSchemaResponse> searchSchema(@RequestBody TableSchemaRequest request) {
        return milvusVectorService.searchSchema(request);
    }
}
