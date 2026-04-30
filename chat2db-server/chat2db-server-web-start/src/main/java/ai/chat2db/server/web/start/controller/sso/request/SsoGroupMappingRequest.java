package ai.chat2db.server.web.start.controller.sso.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SsoGroupMappingRequest {

    private Long id;

    @NotBlank
    private String issuer;

    @NotBlank
    private String externalGroupCode;

    @NotNull
    private Long teamId;
}
