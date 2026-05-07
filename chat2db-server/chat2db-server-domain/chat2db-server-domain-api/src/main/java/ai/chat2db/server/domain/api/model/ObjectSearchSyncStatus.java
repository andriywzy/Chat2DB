package ai.chat2db.server.domain.api.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObjectSearchSyncStatus {

    private Long dataSourceId;

    private String lastSyncStatus;

    private LocalDateTime lastSyncTime;

    private String lastSyncError;

    private Long lastSyncVersion;

    private LocalDateTime nextSyncTime;
}
