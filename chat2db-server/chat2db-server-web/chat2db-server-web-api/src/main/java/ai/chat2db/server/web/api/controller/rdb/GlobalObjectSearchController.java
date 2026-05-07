package ai.chat2db.server.web.api.controller.rdb;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.rdb.request.GlobalObjectSearchRequest;
import ai.chat2db.server.web.api.controller.rdb.service.GlobalObjectSearchService;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rdb/object")
public class GlobalObjectSearchController {

    private final GlobalObjectSearchService globalObjectSearchService;

    public GlobalObjectSearchController(GlobalObjectSearchService globalObjectSearchService) {
        this.globalObjectSearchService = globalObjectSearchService;
    }

    @PostMapping("/global_search")
    public DataResult<GlobalObjectSearchResponse> globalSearch(@Valid @RequestBody GlobalObjectSearchRequest request) {
        return DataResult.of(globalObjectSearchService.search(request));
    }
}
