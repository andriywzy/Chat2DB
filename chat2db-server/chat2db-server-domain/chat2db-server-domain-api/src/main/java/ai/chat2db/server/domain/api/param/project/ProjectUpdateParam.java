package ai.chat2db.server.domain.api.param.project;

import lombok.Data;

@Data
public class ProjectUpdateParam {

    private Long id;

    private String name;

    private String description;
}
