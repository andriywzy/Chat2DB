package ai.chat2db.server.domain.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuditResourceTypeEnum {
    USER("USER"),
    TEAM("TEAM"),
    PROJECT("PROJECT"),
    ENVIRONMENT("ENVIRONMENT"),
    DATASOURCE("DATASOURCE"),
    TEAM_USER("TEAM_USER"),
    TEAM_PROJECT("TEAM_PROJECT"),
    DATABASE("DATABASE");

    private final String code;
}
