package ai.chat2db.server.web.api.controller.ai.platform.sql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlOnlyValidationResult {

    private boolean valid;

    private String message;
}
