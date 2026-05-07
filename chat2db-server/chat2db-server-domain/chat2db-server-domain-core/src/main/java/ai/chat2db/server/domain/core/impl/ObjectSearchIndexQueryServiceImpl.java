package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.model.ObjectSearchIndexItem;
import ai.chat2db.server.domain.api.model.ObjectSearchSyncStatus;
import ai.chat2db.server.domain.api.service.ObjectSearchIndexQueryService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.ObjectSearchIndexDO;
import ai.chat2db.server.domain.repository.entity.ObjectSearchSyncStatusDO;
import ai.chat2db.server.domain.repository.mapper.ObjectSearchIndexMapper;
import ai.chat2db.server.domain.repository.mapper.ObjectSearchSyncStatusMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
public class ObjectSearchIndexQueryServiceImpl implements ObjectSearchIndexQueryService {

    private ObjectSearchIndexMapper getIndexMapper() {
        return Dbutils.getMapper(ObjectSearchIndexMapper.class);
    }

    private ObjectSearchSyncStatusMapper getStatusMapper() {
        return Dbutils.getMapper(ObjectSearchSyncStatusMapper.class);
    }

    @Override
    public List<ObjectSearchIndexItem> listByDataSourceIds(List<Long> dataSourceIds) {
        if (CollectionUtils.isEmpty(dataSourceIds)) {
            return List.of();
        }
        Map<Long, ObjectSearchSyncStatus> statusMap = statusByDataSourceIds(dataSourceIds);
        Map<Long, Long> activeVersions = statusMap.values().stream()
            .filter(Objects::nonNull)
            .filter(status -> status.getLastSyncVersion() != null)
            .collect(Collectors.toMap(ObjectSearchSyncStatus::getDataSourceId, ObjectSearchSyncStatus::getLastSyncVersion));
        if (activeVersions.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ObjectSearchIndexDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ObjectSearchIndexDO::getDataSourceId, activeVersions.keySet())
            .eq(ObjectSearchIndexDO::getDeleted, "N");
        return getIndexMapper().selectList(queryWrapper).stream()
            .filter(item -> Objects.equals(activeVersions.get(item.getDataSourceId()), item.getSyncVersion()))
            .map(this::toItem)
            .toList();
    }

    @Override
    public Map<Long, ObjectSearchSyncStatus> statusByDataSourceIds(List<Long> dataSourceIds) {
        if (CollectionUtils.isEmpty(dataSourceIds)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<ObjectSearchSyncStatusDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ObjectSearchSyncStatusDO::getDataSourceId, dataSourceIds);
        return getStatusMapper().selectList(queryWrapper).stream()
            .collect(Collectors.toMap(ObjectSearchSyncStatusDO::getDataSourceId, this::toStatus, (left, right) -> right, LinkedHashMap::new));
    }

    private ObjectSearchIndexItem toItem(ObjectSearchIndexDO item) {
        return ObjectSearchIndexItem.builder()
            .dataSourceId(item.getDataSourceId())
            .dataSourceName(item.getDataSourceNameSnapshot())
            .databaseType(item.getDatabaseType())
            .databaseName(item.getDatabaseName())
            .schemaName(item.getSchemaName())
            .objectType(item.getObjectType())
            .objectName(item.getObjectName())
            .comment(item.getComment())
            .build();
    }

    private ObjectSearchSyncStatus toStatus(ObjectSearchSyncStatusDO statusDO) {
        return ObjectSearchSyncStatus.builder()
            .dataSourceId(statusDO.getDataSourceId())
            .lastSyncStatus(statusDO.getLastSyncStatus())
            .lastSyncTime(statusDO.getLastSyncTime() == null ? null : statusDO.getLastSyncTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .lastSyncError(statusDO.getLastSyncError())
            .lastSyncVersion(statusDO.getLastSyncVersion())
            .nextSyncTime(statusDO.getNextSyncTime() == null ? null : statusDO.getNextSyncTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            .build();
    }
}
