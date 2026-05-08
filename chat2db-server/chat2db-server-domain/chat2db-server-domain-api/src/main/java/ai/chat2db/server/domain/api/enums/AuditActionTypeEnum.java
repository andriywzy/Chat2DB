package ai.chat2db.server.domain.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditActionTypeEnum {
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    GRANT("GRANT"),
    EXECUTE("EXECUTE");

    private final String code;
}
