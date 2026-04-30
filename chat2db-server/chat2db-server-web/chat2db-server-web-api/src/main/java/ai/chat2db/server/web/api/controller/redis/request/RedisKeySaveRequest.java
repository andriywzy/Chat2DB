package ai.chat2db.server.web.api.controller.redis.request;

import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RedisKeySaveRequest extends DataSourceBaseRequest {

    /**
     * Old key name before rename.
     */
    private String originalKeyName;

    @NotBlank
    private String keyName;

    @NotBlank
    private String keyType;

    /**
     * ttl seconds. -1 or <=0 means persist.
     */
    private Long ttlSeconds;

    private String stringValue;

    @Valid
    private List<FieldValue> hashValues;

    private List<String> listValues;

    private List<String> setValues;

    @Valid
    private List<ZSetValue> zsetValues;

    @Valid
    private List<StreamValue> streamValues;

    @Data
    public static class FieldValue {
        private String field;
        private String value;
    }

    @Data
    public static class ZSetValue {
        private String member;
        private String score;
    }

    @Data
    public static class StreamValue {
        private String id;
        @Valid
        private List<FieldValue> values;
    }
}

