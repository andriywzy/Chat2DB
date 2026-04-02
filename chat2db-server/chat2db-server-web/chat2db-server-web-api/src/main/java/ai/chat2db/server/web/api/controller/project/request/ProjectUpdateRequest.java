package ai.chat2db.server.web.api.controller.project.request;

import lombok.Data;

@Data
public class ProjectUpdateRequest {

    private Long id;

    private String name;

    private String description;
}
