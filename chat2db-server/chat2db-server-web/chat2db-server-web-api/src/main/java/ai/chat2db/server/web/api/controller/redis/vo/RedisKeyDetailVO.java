package ai.chat2db.server.web.api.controller.redis.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RedisKeyDetailVO {

    private String keyName;

    private String keyType;

    private Long ttlSeconds;

    private String stringValue;

    private List<FieldValueVO> hashValues;

    private List<String> listValues;

    private List<String> setValues;

    private List<ZSetValueVO> zsetValues;

    private List<StreamValueVO> streamValues;

    @Data
    @Builder
    public static class FieldValueVO {
        private String field;
        private String value;
    }

    @Data
    @Builder
    public static class ZSetValueVO {
        private String member;
        private String score;
    }

    @Data
    @Builder
    public static class StreamValueVO {
        private String id;
        private List<FieldValueVO> values;
    }
}

