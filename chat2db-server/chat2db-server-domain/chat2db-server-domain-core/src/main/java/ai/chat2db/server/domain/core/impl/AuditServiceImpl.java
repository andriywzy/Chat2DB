package ai.chat2db.server.domain.core.impl;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.server.domain.api.enums.AuditCategoryEnum;
import ai.chat2db.server.domain.api.model.AuditRecord;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.domain.api.model.DatabaseAuditEvent;
import ai.chat2db.server.domain.api.param.audit.AuditPageQueryParam;
import ai.chat2db.server.domain.api.service.AuditService;
import ai.chat2db.server.domain.api.service.ConsoleAuditWriter;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.UserService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.AuditLogDO;
import ai.chat2db.server.domain.repository.entity.OperationLogDO;
import ai.chat2db.server.domain.repository.mapper.AuditLogMapper;
import ai.chat2db.server.domain.repository.mapper.OperationLogMapper;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;

@Service
@Slf4j
public class AuditServiceImpl implements AuditService {

    @Resource
    private ConsoleAuditWriter consoleAuditWriter;

    @Resource
    private UserService userService;

    @Resource
    private DataSourceService dataSourceService;

    private AuditLogMapper getMapper() {
        return Dbutils.getMapper(AuditLogMapper.class);
    }

    private OperationLogMapper getOperationLogMapper() {
        return Dbutils.getMapper(OperationLogMapper.class);
    }

    @Override
    public DataResult<Long> createConsoleAudit(ConsoleAuditCreateRequest request) {
        return consoleAuditWriter.write(request);
    }

    @Override
    public PageResult<AuditRecord> queryPage(AuditPageQueryParam param) {
        if (AuditCategoryEnum.DATABASE.getCode().equalsIgnoreCase(param.getCategory())) {
            return queryDatabasePage(param);
        }
        LambdaQueryWrapper<AuditLogDO> queryWrapper = buildQueryWrapper(param);
        Page<AuditLogDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        IPage<AuditLogDO> pageResult = getMapper().selectPage(page, queryWrapper);
        List<AuditRecord> rows = pageResult.getRecords().stream().map(this::toAuditRecord).toList();
        return PageResult.of(rows, pageResult.getTotal(), param);
    }

    @Override
    public DataResult<AuditRecord> queryDetail(String id) {
        if (StringUtils.startsWith(id, "D_")) {
            String rawId = StringUtils.removeStart(id, "D_");
            if (StringUtils.isBlank(rawId)) {
                return DataResult.of(null);
            }
            OperationLogDO data = getOperationLogMapper().selectById(Long.parseLong(rawId));
            return DataResult.of(data == null ? null : toDatabaseAuditRecord(data, loadUserNameMap(List.of(data.getUserId())),
                loadDataSourceNameMap(List.of(data.getDataSourceId()))));
        }
        String rawId = extractRawId(id);
        if (StringUtils.isBlank(rawId)) {
            return DataResult.of(null);
        }
        AuditLogDO data = getMapper().selectById(Long.parseLong(rawId));
        return DataResult.of(data == null ? null : toAuditRecord(data));
    }

    private LambdaQueryWrapper<AuditLogDO> buildQueryWrapper(AuditPageQueryParam param) {
        LambdaQueryWrapper<AuditLogDO> queryWrapper = new LambdaQueryWrapper<>();
        Date startTime = param.getStartTime() == null ? null : new Date(param.getStartTime());
        Date endTime = param.getEndTime() == null ? null : new Date(param.getEndTime());
        queryWrapper.eq(StringUtils.isNotBlank(param.getCategory()), AuditLogDO::getCategory, param.getCategory())
            .eq(StringUtils.isNotBlank(param.getResourceType()), AuditLogDO::getResourceType, param.getResourceType())
            .eq(StringUtils.isNotBlank(param.getStatus()), AuditLogDO::getStatus, param.getStatus())
            .eq(param.getOperatorUserId() != null, AuditLogDO::getOperatorUserId, param.getOperatorUserId())
            .eq(StringUtils.isNotBlank(param.getTargetId()), AuditLogDO::getTargetId, param.getTargetId())
            .eq(param.getDataSourceId() != null, AuditLogDO::getTargetId, String.valueOf(param.getDataSourceId()))
            .ge(startTime != null, AuditLogDO::getGmtCreate, startTime)
            .le(endTime != null, AuditLogDO::getGmtCreate, endTime)
            .orderByDesc(AuditLogDO::getGmtCreate, AuditLogDO::getId);
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(AuditLogDO::getOperatorUserName, param.getSearchKey())
                .or().like(AuditLogDO::getTargetName, param.getSearchKey())
                .or().like(AuditLogDO::getDetailSummary, param.getSearchKey())
                .or().like(AuditLogDO::getDetailPayload, param.getSearchKey())
                .or().like(AuditLogDO::getRequestId, param.getSearchKey()));
        }
        return queryWrapper;
    }

    private PageResult<AuditRecord> queryDatabasePage(AuditPageQueryParam param) {
        LambdaQueryWrapper<OperationLogDO> queryWrapper = buildDatabaseQueryWrapper(param);
        Page<OperationLogDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        IPage<OperationLogDO> pageResult = getOperationLogMapper().selectPage(page, queryWrapper);
        List<OperationLogDO> records = pageResult.getRecords();
        Map<Long, String> userNameMap = loadUserNameMap(records.stream().map(OperationLogDO::getUserId).toList());
        Map<Long, String> dataSourceNameMap = loadDataSourceNameMap(records.stream().map(OperationLogDO::getDataSourceId).toList());
        List<AuditRecord> rows = records.stream()
            .map(record -> toDatabaseAuditRecord(record, userNameMap, dataSourceNameMap))
            .toList();
        return PageResult.of(rows, pageResult.getTotal(), param);
    }

    private LambdaQueryWrapper<OperationLogDO> buildDatabaseQueryWrapper(AuditPageQueryParam param) {
        LambdaQueryWrapper<OperationLogDO> queryWrapper = new LambdaQueryWrapper<>();
        Date startTime = param.getStartTime() == null ? null : new Date(param.getStartTime());
        Date endTime = param.getEndTime() == null ? null : new Date(param.getEndTime());
        queryWrapper.eq(param.getOperatorUserId() != null, OperationLogDO::getUserId, param.getOperatorUserId())
            .eq(param.getDataSourceId() != null, OperationLogDO::getDataSourceId, param.getDataSourceId())
            .eq(StringUtils.isNotBlank(param.getStatus()), OperationLogDO::getStatus, toOperationLogStatus(param.getStatus()))
            .ge(startTime != null, OperationLogDO::getGmtCreate, toLocalDateTime(startTime))
            .le(endTime != null, OperationLogDO::getGmtCreate, toLocalDateTime(endTime))
            .orderByDesc(OperationLogDO::getGmtCreate, OperationLogDO::getId);
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(OperationLogDO::getDdl, param.getSearchKey())
                .or().like(OperationLogDO::getDatabaseName, param.getSearchKey())
                .or().like(OperationLogDO::getSchemaName, param.getSearchKey()));
        }
        return queryWrapper;
    }

    private AuditRecord toAuditRecord(AuditLogDO data) {
        AuditRecord record = new AuditRecord();
        boolean databaseAudit = AuditCategoryEnum.DATABASE.getCode().equalsIgnoreCase(data.getCategory());
        record.setId((databaseAudit ? "D_" : "C_") + data.getId());
        record.setCategory(data.getCategory());
        record.setActionType(data.getActionType());
        record.setResourceType(data.getResourceType());
        record.setOperatorUserId(data.getOperatorUserId());
        record.setOperatorUserName(data.getOperatorUserName());
        record.setRoleCode(data.getRoleCode());
        record.setTargetId(data.getTargetId());
        record.setTargetName(data.getTargetName());
        record.setStatus(data.getStatus());
        record.setOccurredAt(data.getGmtCreate());
        record.setDetailSummary(data.getDetailSummary());
        record.setDetailPayload(data.getDetailPayload());
        record.setRequestId(data.getRequestId());
        record.setRequestPath(data.getRequestPath());
        record.setRequestMethod(data.getRequestMethod());
        record.setClientIp(data.getClientIp());
        record.setUserAgent(data.getUserAgent());
        record.setErrorMessage(data.getErrorMessage());
        if (databaseAudit) {
            record.setDataSourceId(StringUtils.isBlank(data.getTargetId()) ? null : Long.valueOf(data.getTargetId()));
            record.setDataSourceName(data.getTargetName());
            DatabaseAuditEvent event = parseDatabaseEvent(data.getDetailPayload());
            if (event != null) {
                record.setSqlType(event.getSqlType());
                record.setDurationMs(event.getDurationMs());
                record.setOperationRows(event.getOperationRows());
            }
        }
        return record;
    }

    private AuditRecord toDatabaseAuditRecord(OperationLogDO data, Map<Long, String> userNameMap, Map<Long, String> dataSourceNameMap) {
        AuditRecord record = new AuditRecord();
        record.setId("D_" + data.getId());
        record.setCategory(AuditCategoryEnum.DATABASE.getCode());
        record.setActionType("EXECUTE");
        record.setResourceType("DATABASE");
        record.setOperatorUserId(data.getUserId());
        record.setOperatorUserName(userNameMap.getOrDefault(data.getUserId(), data.getUserId() == null ? null : String.valueOf(data.getUserId())));
        record.setTargetId(data.getDataSourceId() == null ? null : String.valueOf(data.getDataSourceId()));
        record.setTargetName(dataSourceNameMap.getOrDefault(data.getDataSourceId(), record.getTargetId()));
        record.setStatus(fromOperationLogStatus(data.getStatus()));
        record.setOccurredAt(data.getGmtCreate() == null ? null : Date.from(data.getGmtCreate().atZone(ZoneId.systemDefault()).toInstant()));
        record.setDetailSummary(buildOperationLogSummary(data.getDdl()));
        record.setDetailPayload(buildOperationLogPayload(data));
        record.setDataSourceId(data.getDataSourceId());
        record.setDataSourceName(record.getTargetName());
        record.setDurationMs(data.getUseTime());
        record.setOperationRows(data.getOperationRows());
        return record;
    }

    private DatabaseAuditEvent parseDatabaseEvent(String payload) {
        if (StringUtils.isBlank(payload)) {
            return null;
        }
        try {
            return JSON.parseObject(payload, DatabaseAuditEvent.class);
        } catch (Exception e) {
            log.warn("parse database audit payload error", e);
            return null;
        }
    }

    private String extractRawId(String id) {
        if (StringUtils.startsWith(id, "C_")) {
            return StringUtils.removeStart(id, "C_");
        }
        if (StringUtils.startsWith(id, "D_")) {
            return StringUtils.removeStart(id, "D_");
        }
        return null;
    }

    private Map<Long, String> loadUserNameMap(List<Long> userIds) {
        List<Long> filteredIds = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        return userService.listQuery(filteredIds).getData().stream().collect(Collectors.toMap(
            user -> user.getId(),
            user -> StringUtils.defaultIfBlank(user.getNickName(), user.getUserName()),
            (left, right) -> left
        ));
    }

    private Map<Long, String> loadDataSourceNameMap(List<Long> dataSourceIds) {
        List<Long> filteredIds = dataSourceIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        return dataSourceService.queryByIds(filteredIds).getData().stream().collect(Collectors.toMap(
            dataSource -> dataSource.getId(),
            dataSource -> dataSource.getAlias(),
            (left, right) -> left
        ));
    }

    private String toOperationLogStatus(String auditStatus) {
        if (StringUtils.equalsIgnoreCase("SUCCESS", auditStatus)) {
            return "success";
        }
        if (StringUtils.equalsIgnoreCase("FAILED", auditStatus)) {
            return "fail";
        }
        return auditStatus;
    }

    private String fromOperationLogStatus(String status) {
        if (StringUtils.equalsIgnoreCase("success", status)) {
            return "SUCCESS";
        }
        if (StringUtils.equalsIgnoreCase("fail", status)) {
            return "FAILED";
        }
        return StringUtils.upperCase(status);
    }

    private java.time.LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String buildOperationLogSummary(String ddl) {
        String sql = StringUtils.normalizeSpace(ddl);
        return StringUtils.isBlank(sql) ? "-" : StringUtils.abbreviate(sql, 200);
    }

    private String buildOperationLogPayload(OperationLogDO data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", data.getId());
        payload.put("dataSourceId", data.getDataSourceId());
        payload.put("databaseName", data.getDatabaseName());
        payload.put("schemaName", data.getSchemaName());
        payload.put("sql", data.getDdl());
        payload.put("status", data.getStatus());
        payload.put("operationRows", data.getOperationRows());
        payload.put("durationMs", data.getUseTime());
        payload.put("extendInfo", data.getExtendInfo());
        payload.put("gmtCreate", data.getGmtCreate());
        return JSON.toJSONString(payload);
    }
}
