package ai.chat2db.server.web.api.controller.project.request;

import lombok.Data;

@Data
public class ProjectCreateRequest {

    private String name;

    private String description;

    private String scopeType;

    private Long scopeId;
}
