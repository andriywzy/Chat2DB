package ai.chat2db.server.domain.core.impl;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.server.domain.api.enums.AuditCategoryEnum;
import ai.chat2db.server.domain.api.model.DatabaseAuditEvent;
import ai.chat2db.server.domain.api.service.DatabaseAuditWriter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.AuditLogDO;
import ai.chat2db.server.domain.repository.mapper.AuditLogMapper;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseAuditWriterImpl implements DatabaseAuditWriter {

    private static final int RETENTION_DAYS = 180;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chat2db-db-audit-writer");
        thread.setDaemon(true);
        return thread;
    });

    private AuditLogMapper getMapper() {
        return Dbutils.getMapper(AuditLogMapper.class);
    }

    @Override
    public void write(DatabaseAuditEvent event) {
        if (event == null) {
            return;
        }
        executorService.submit(() -> doWrite(event));
    }

    private void doWrite(DatabaseAuditEvent event) {
        try {
            AuditLogDO data = new AuditLogDO();
            Date occurredAt = event.getTimestamp() == null ? new Date() : event.getTimestamp();
            data.setGmtCreate(occurredAt);
            data.setGmtModified(occurredAt);
            data.setCategory(AuditCategoryEnum.DATABASE.getCode());
            data.setActionType("EXECUTE");
            data.setResourceType("DATABASE");
            data.setOperatorUserId(event.getUserId());
            data.setOperatorUserName(event.getUserName());
            data.setRoleCode(event.getRoleCode());
            data.setTargetId(event.getDataSourceId() == null ? null : String.valueOf(event.getDataSourceId()));
            data.setTargetName(event.getDataSourceName());
            data.setRequestId(event.getRequestId());
            data.setClientIp(event.getClientIp());
            data.setUserAgent(event.getClientType());
            data.setStatus(event.getStatus());
            data.setDetailSummary(buildDatabaseSummary(event));
            data.setDetailPayload(JSON.toJSONString(event));
            data.setErrorMessage(event.getErrorMessage());
            getMapper().insert(data);
        } catch (Exception e) {
            log.error("write database audit log error", e);
        }
    }

    @Scheduled(initialDelay = 60000L, fixedDelay = 86400000L)
    public void cleanupExpiredRecords() {
        Date expireBefore = Date.from(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS));
        try {
            LambdaQueryWrapper<AuditLogDO> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(AuditLogDO::getCategory, AuditCategoryEnum.DATABASE.getCode())
                .lt(AuditLogDO::getGmtCreate, expireBefore);
            getMapper().delete(queryWrapper);
        } catch (Exception e) {
            log.error("cleanup database audit records error", e);
        }
    }

    private String buildDatabaseSummary(DatabaseAuditEvent event) {
        String sql = org.apache.commons.lang3.StringUtils.normalizeSpace(event.getSql());
        if (org.apache.commons.lang3.StringUtils.isNotBlank(sql)) {
            return org.apache.commons.lang3.StringUtils.abbreviate(sql, 200);
        }
        return org.apache.commons.lang3.StringUtils.defaultIfBlank(event.getSqlType(), "SQL");
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
