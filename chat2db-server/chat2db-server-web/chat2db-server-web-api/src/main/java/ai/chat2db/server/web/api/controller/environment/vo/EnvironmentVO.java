package ai.chat2db.server.web.api.controller.environment.vo;

import lombok.Data;

@Data
public class EnvironmentVO {

    private Long id;

    private String name;

    private String shortName;

    private String color;

    private String scopeType;

    private Long scopeId;

    private Boolean canManage;

    private Long projectId;
}
