package ai.chat2db.server.web.api.controller.redis.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RedisKeyDeleteRequest extends DataSourceBaseRequest {

    @NotEmpty
    private List<String> keyNames;
}
