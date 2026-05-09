package ai.chat2db.server.domain.api.enums;

import lombok.Getter;

@Getter
public enum ProjectPermissionTypeEnum {
    VIEW("VIEW"),
    TEAM_ADMIN("TEAM_ADMIN");

    private final String code;

    ProjectPermissionTypeEnum(String code) {
        this.code = code;
    }

    public static String normalize(String permissionType) {
        if (TEAM_ADMIN.code.equalsIgnoreCase(permissionType)) {
            return TEAM_ADMIN.code;
        }
        return VIEW.code;
    }
}
