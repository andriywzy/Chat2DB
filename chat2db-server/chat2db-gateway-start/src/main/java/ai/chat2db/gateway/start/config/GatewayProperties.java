package ai.chat2db.gateway.start.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private String upstreamBaseUrl;
}
