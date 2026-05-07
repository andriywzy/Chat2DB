package ai.chat2db.server.domain.api.service;

import ai.chat2db.server.domain.api.model.ObjectSearchIndexItem;
import ai.chat2db.server.domain.api.model.ObjectSearchSyncStatus;
import java.util.List;
import java.util.Map;

public interface ObjectSearchIndexQueryService {

    List<ObjectSearchIndexItem> listByDataSourceIds(List<Long> dataSourceIds);

    Map<Long, ObjectSearchSyncStatus> statusByDataSourceIds(List<Long> dataSourceIds);
}
