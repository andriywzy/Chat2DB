package ai.chat2db.server.web.api.controller.redis.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedisKeyVO {

    private String keyName;

    private String keyType;

    private String valuePreview;

    private Long ttlSeconds;
}
