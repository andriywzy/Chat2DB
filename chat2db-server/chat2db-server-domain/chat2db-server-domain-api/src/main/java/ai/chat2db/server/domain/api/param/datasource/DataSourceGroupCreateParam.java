package ai.chat2db.server.domain.api.param.datasource;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSourceGroupCreateParam {

    @NotBlank
    private String name;
}
