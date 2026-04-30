package ai.chat2db.server.web.api.controller.project.vo;

import lombok.Data;

@Data
public class ProjectVO {

    private Long id;

    private String name;

    private String description;

    private String scopeType;

    private Long scopeId;

    private Boolean canManage;
}
