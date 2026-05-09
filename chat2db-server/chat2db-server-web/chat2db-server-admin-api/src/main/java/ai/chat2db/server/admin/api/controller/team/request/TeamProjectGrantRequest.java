package ai.chat2db.server.admin.api.controller.team.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamProjectGrantRequest {

    @NotNull
    private Long projectId;

    private String permissionType;

    private List<Long> environmentIdList;
}
