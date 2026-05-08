package ai.chat2db.server.domain.core.impl;

import com.alibaba.fastjson2.JSON;
import ai.chat2db.server.domain.api.model.DatabaseAuditEvent;
import ai.chat2db.server.domain.api.service.DatabaseAuditWriter;
import ai.chat2db.server.tools.common.config.Chat2dbProperties;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DatabaseAuditWriterImpl implements DatabaseAuditWriter {

    private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chat2db-db-audit-writer");
        thread.setDaemon(true);
        return thread;
    });

    @Resource
    private Chat2dbProperties chat2dbProperties;

    @Override
    public void write(DatabaseAuditEvent event) {
        if (Boolean.FALSE.equals(chat2dbProperties.getAudit().getDbFile().getEnabled())) {
            return;
        }
        executorService.submit(() -> doWrite(event));
    }

    private void doWrite(DatabaseAuditEvent event) {
        try {
            Files.createDirectories(getBasePath());
            Path filePath = getBasePath().resolve("db-audit-" + getFileDate(event) + ".log");
            Files.writeString(filePath, JSON.toJSONString(event) + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("write database audit log error", e);
        }
    }

    @Scheduled(initialDelay = 60000L, fixedDelay = 86400000L)
    public void cleanupExpiredFiles() {
        if (Boolean.FALSE.equals(chat2dbProperties.getAudit().getDbFile().getEnabled())) {
            return;
        }
        Integer retentionDays = chat2dbProperties.getAudit().getDbFile().getRetentionDays();
        if (retentionDays == null || retentionDays <= 0) {
            return;
        }
        Path basePath = getBasePath();
        if (!Files.exists(basePath)) {
            return;
        }
        LocalDate expireBefore = LocalDate.now().minusDays(retentionDays.longValue());
        try (var paths = Files.list(basePath)) {
            paths.filter(path -> path.getFileName().toString().startsWith("db-audit-"))
                .sorted(Comparator.naturalOrder())
                .forEach(path -> deleteIfExpired(path, expireBefore));
        } catch (Exception e) {
            log.error("cleanup database audit log error", e);
        }
    }

    private void deleteIfExpired(Path path, LocalDate expireBefore) {
        String fileName = path.getFileName().toString();
        String datePart = StringUtils.removeStart(fileName, "db-audit-");
        datePart = StringUtils.removeEnd(datePart, ".log");
        try {
            LocalDate fileDate = LocalDate.parse(datePart, FILE_DATE_FORMATTER);
            if (fileDate.isBefore(expireBefore)) {
                Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            log.warn("skip invalid audit file {}", fileName, e);
        }
    }

    private String getFileDate(DatabaseAuditEvent event) {
        return event.getTimestamp()
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(FILE_DATE_FORMATTER);
    }

    private Path getBasePath() {
        String basePath = StringUtils.defaultIfBlank(chat2dbProperties.getAudit().getDbFile().getBasePath(),
            "~/.chat2db/audit/db");
        if (basePath.startsWith("~/")) {
            basePath = System.getProperty("user.home") + basePath.substring(1);
        }
        return Paths.get(basePath);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
