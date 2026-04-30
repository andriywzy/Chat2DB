package ai.chat2db.server.web.api.controller.redis.request;

import ai.chat2db.server.tools.base.wrapper.request.PageQueryRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequestInfo;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;

@Data
public class RedisKeyPageRequest extends PageQueryRequest implements DataSourceBaseRequestInfo {

    @Serial
    private static final long serialVersionUID = 127461615688887516L;

    @NotNull
    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    private String searchKey;

    private boolean refresh;
}
