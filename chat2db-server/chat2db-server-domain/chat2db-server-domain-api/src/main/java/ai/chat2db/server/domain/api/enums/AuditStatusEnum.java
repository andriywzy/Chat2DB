package ai.chat2db.server.domain.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditStatusEnum {
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String code;
}
