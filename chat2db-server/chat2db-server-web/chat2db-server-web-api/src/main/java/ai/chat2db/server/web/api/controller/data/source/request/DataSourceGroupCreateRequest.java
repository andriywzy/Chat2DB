package ai.chat2db.server.web.api.controller.data.source.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSourceGroupCreateRequest {

    @NotBlank
    private String name;
}
