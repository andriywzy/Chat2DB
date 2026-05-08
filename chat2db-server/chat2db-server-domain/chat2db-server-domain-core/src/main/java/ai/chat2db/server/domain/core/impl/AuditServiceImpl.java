package ai.chat2db.server.domain.core.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
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
import ai.chat2db.server.tools.common.config.Chat2dbProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;

@Service
@Slf4j
public class AuditServiceImpl implements AuditService {

    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ConsoleAuditWriter consoleAuditWriter;

    @Resource
    private Chat2dbProperties chat2dbProperties;

    private AuditLogMapper getMapper() {
        return Dbutils.getMapper(AuditLogMapper.class);
    }

    @Override
    public DataResult<Long> createConsoleAudit(ConsoleAuditCreateRequest request) {
        return consoleAuditWriter.write(request);
    }

    @Override
    public PageResult<AuditRecord> queryPage(AuditPageQueryParam param) {
        List<AuditRecord> allRecords = new ArrayList<>();
        if (StringUtils.isBlank(param.getCategory()) || AuditCategoryEnum.CONSOLE.getCode().equalsIgnoreCase(param.getCategory())) {
            allRecords.addAll(queryConsoleRecords(param));
        }
        if (StringUtils.isBlank(param.getCategory()) || AuditCategoryEnum.DATABASE.getCode().equalsIgnoreCase(param.getCategory())) {
            allRecords.addAll(queryDatabaseRecords(param));
        }
        allRecords.sort(Comparator.comparing(AuditRecord::getOccurredAt, Comparator.nullsLast(Date::compareTo)).reversed());
        int fromIndex = Math.max((param.getPageNo() - 1) * param.getPageSize(), 0);
        int toIndex = Math.min(fromIndex + param.getPageSize(), allRecords.size());
        List<AuditRecord> page = fromIndex >= allRecords.size() ? List.of() : allRecords.subList(fromIndex, toIndex);
        return PageResult.of(page, (long) allRecords.size(), param);
    }

    @Override
    public DataResult<AuditRecord> queryDetail(String id) {
        if (StringUtils.startsWith(id, "C_")) {
            AuditLogDO data = getMapper().selectById(Long.parseLong(StringUtils.removeStart(id, "C_")));
            return DataResult.of(data == null ? null : toAuditRecord(data));
        }
        if (StringUtils.startsWith(id, "D_")) {
            return DataResult.of(findDatabaseRecord(id));
        }
        return DataResult.of(null);
    }

    private List<AuditRecord> queryConsoleRecords(AuditPageQueryParam param) {
        LambdaQueryWrapper<AuditLogDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AuditLogDO::getCategory, AuditCategoryEnum.CONSOLE.getCode())
            .eq(StringUtils.isNotBlank(param.getResourceType()), AuditLogDO::getResourceType, param.getResourceType())
            .eq(StringUtils.isNotBlank(param.getStatus()), AuditLogDO::getStatus, param.getStatus())
            .eq(param.getOperatorUserId() != null, AuditLogDO::getOperatorUserId, param.getOperatorUserId())
            .eq(StringUtils.isNotBlank(param.getTargetId()), AuditLogDO::getTargetId, param.getTargetId())
            .ge(param.getStartTime() != null, AuditLogDO::getGmtCreate, new Date(param.getStartTime()))
            .le(param.getEndTime() != null, AuditLogDO::getGmtCreate, new Date(param.getEndTime()))
            .orderByDesc(AuditLogDO::getGmtCreate, AuditLogDO::getId);
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(AuditLogDO::getOperatorUserName, param.getSearchKey())
                .or().like(AuditLogDO::getTargetName, param.getSearchKey())
                .or().like(AuditLogDO::getDetailSummary, param.getSearchKey()));
        }
        return getMapper().selectList(queryWrapper).stream().map(this::toAuditRecord).toList();
    }

    private List<AuditRecord> queryDatabaseRecords(AuditPageQueryParam param) {
        Path basePath = getBasePath();
        if (!Files.exists(basePath)) {
            return List.of();
        }
        List<AuditRecord> result = new ArrayList<>();
        try (var paths = Files.list(basePath)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().startsWith("db-audit-")).toList()) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    DatabaseAuditEvent event = JSON.parseObject(line, new TypeReference<DatabaseAuditEvent>() {});
                    AuditRecord record = toAuditRecord(event);
                    if (match(record, param)) {
                        result.add(record);
                    }
                }
            }
        } catch (Exception e) {
            log.error("query database audit log error", e);
        }
        return result;
    }

    private boolean match(AuditRecord record, AuditPageQueryParam param) {
        if (record == null) {
            return false;
        }
        if (StringUtils.isNotBlank(param.getResourceType()) && !StringUtils.equals(record.getResourceType(), param.getResourceType())) {
            return false;
        }
        if (StringUtils.isNotBlank(param.getStatus()) && !StringUtils.equals(record.getStatus(), param.getStatus())) {
            return false;
        }
        if (param.getOperatorUserId() != null && !Objects.equals(record.getOperatorUserId(), param.getOperatorUserId())) {
            return false;
        }
        if (param.getDataSourceId() != null && !Objects.equals(record.getDataSourceId(), param.getDataSourceId())) {
            return false;
        }
        if (param.getStartTime() != null && (record.getOccurredAt() == null || record.getOccurredAt().getTime() < param.getStartTime())) {
            return false;
        }
        if (param.getEndTime() != null && (record.getOccurredAt() == null || record.getOccurredAt().getTime() > param.getEndTime())) {
            return false;
        }
        if (StringUtils.isBlank(param.getSearchKey())) {
            return true;
        }
        String searchKey = param.getSearchKey().toLowerCase();
        return StringUtils.containsIgnoreCase(record.getOperatorUserName(), searchKey)
            || StringUtils.containsIgnoreCase(record.getTargetName(), searchKey)
            || StringUtils.containsIgnoreCase(record.getDetailSummary(), searchKey)
            || StringUtils.containsIgnoreCase(record.getDetailPayload(), searchKey)
            || StringUtils.containsIgnoreCase(record.getRequestId(), searchKey);
    }

    private AuditRecord findDatabaseRecord(String id) {
        Path basePath = getBasePath();
        if (!Files.exists(basePath)) {
            return null;
        }
        try (var paths = Files.list(basePath)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().startsWith("db-audit-")).toList()) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (StringUtils.isBlank(line)) {
                        continue;
                    }
                    DatabaseAuditEvent event = JSON.parseObject(line, new TypeReference<DatabaseAuditEvent>() {});
                    if (StringUtils.equals("D_" + event.getId(), id)) {
                        return toAuditRecord(event);
                    }
                }
            }
        } catch (Exception e) {
            log.error("query database audit detail error", e);
        }
        return null;
    }

    private AuditRecord toAuditRecord(AuditLogDO data) {
        AuditRecord record = new AuditRecord();
        record.setId("C_" + data.getId());
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
        return record;
    }

    private AuditRecord toAuditRecord(DatabaseAuditEvent event) {
        AuditRecord record = new AuditRecord();
        record.setId("D_" + event.getId());
        record.setCategory(AuditCategoryEnum.DATABASE.getCode());
        record.setActionType("EXECUTE");
        record.setResourceType("DATABASE");
        record.setOperatorUserId(event.getUserId());
        record.setOperatorUserName(event.getUserName());
        record.setRoleCode(event.getRoleCode());
        record.setTargetId(event.getDataSourceId() == null ? null : String.valueOf(event.getDataSourceId()));
        record.setTargetName(event.getDataSourceName());
        record.setStatus(event.getStatus());
        record.setOccurredAt(event.getTimestamp());
        record.setDetailSummary(buildDatabaseSummary(event));
        record.setDetailPayload(JSON.toJSONString(event));
        record.setRequestId(event.getRequestId());
        record.setClientIp(event.getClientIp());
        record.setDataSourceId(event.getDataSourceId());
        record.setDataSourceName(event.getDataSourceName());
        record.setSqlType(event.getSqlType());
        record.setDurationMs(event.getDurationMs());
        record.setOperationRows(event.getOperationRows());
        record.setErrorMessage(event.getErrorMessage());
        return record;
    }

    private String buildDatabaseSummary(DatabaseAuditEvent event) {
        return String.format("%s %s [%s/%s]",
            StringUtils.defaultIfBlank(event.getSqlType(), "SQL"),
            StringUtils.defaultIfBlank(event.getDataSourceName(), "-"),
            StringUtils.defaultIfBlank(event.getDatabaseName(), "-"),
            StringUtils.defaultIfBlank(event.getSchemaName(), "-"));
    }

    private Path getBasePath() {
        String basePath = StringUtils.defaultIfBlank(chat2dbProperties.getAudit().getDbFile().getBasePath(),
            "~/.chat2db/audit/db");
        if (basePath.startsWith("~/")) {
            basePath = System.getProperty("user.home") + basePath.substring(1);
        }
        return Paths.get(basePath);
    }
}
