package ai.chat2db.server.web.api.controller.rdb.service;

import ai.chat2db.server.web.api.controller.rdb.request.GlobalObjectSearchRequest;
import ai.chat2db.server.web.api.controller.rdb.vo.GlobalObjectSearchResponse;

public interface GlobalObjectSearchService {

    GlobalObjectSearchResponse search(GlobalObjectSearchRequest request);
}
