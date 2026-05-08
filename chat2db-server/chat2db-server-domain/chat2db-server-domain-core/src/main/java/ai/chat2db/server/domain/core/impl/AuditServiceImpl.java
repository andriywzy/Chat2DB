package ai.chat2db.server.domain.core.impl;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.server.domain.api.enums.AuditCategoryEnum;
import ai.chat2db.server.domain.api.model.AuditRecord;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.domain.api.model.DatabaseAuditEvent;
import ai.chat2db.server.domain.api.param.audit.AuditPageQueryParam;
import ai.chat2db.server.domain.api.service.AuditService;
import ai.chat2db.server.domain.api.service.ConsoleAuditWriter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.AuditLogDO;
import ai.chat2db.server.domain.repository.mapper.AuditLogMapper;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import java.util.Date;
import java.util.List;
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

    private AuditLogMapper getMapper() {
        return Dbutils.getMapper(AuditLogMapper.class);
    }

    @Override
    public DataResult<Long> createConsoleAudit(ConsoleAuditCreateRequest request) {
        return consoleAuditWriter.write(request);
    }

    @Override
    public PageResult<AuditRecord> queryPage(AuditPageQueryParam param) {
        LambdaQueryWrapper<AuditLogDO> queryWrapper = buildQueryWrapper(param);
        Page<AuditLogDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        IPage<AuditLogDO> pageResult = getMapper().selectPage(page, queryWrapper);
        List<AuditRecord> rows = pageResult.getRecords().stream().map(this::toAuditRecord).toList();
        return PageResult.of(rows, pageResult.getTotal(), param);
    }

    @Override
    public DataResult<AuditRecord> queryDetail(String id) {
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
}
