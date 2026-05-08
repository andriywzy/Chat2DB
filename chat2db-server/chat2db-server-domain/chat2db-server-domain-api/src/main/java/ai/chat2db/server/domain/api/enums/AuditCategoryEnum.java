package ai.chat2db.server.domain.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditCategoryEnum {
    CONSOLE("CONSOLE"),
    DATABASE("DATABASE");

    private final String code;
}
