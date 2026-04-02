package ai.chat2db.server.domain.api.param.project;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectCreateParam {

    @NotBlank
    private String name;

    private String description;

    private String scopeType;

    private Long scopeId;
}
