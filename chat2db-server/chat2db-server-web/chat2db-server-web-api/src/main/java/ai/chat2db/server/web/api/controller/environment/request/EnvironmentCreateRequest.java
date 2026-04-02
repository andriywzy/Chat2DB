package ai.chat2db.server.web.api.controller.environment.request;

import lombok.Data;

@Data
public class EnvironmentCreateRequest {

    private String name;

    private String shortName;

    private String color;

    private String scopeType;

    private Long scopeId;

    private Long projectId;
}
