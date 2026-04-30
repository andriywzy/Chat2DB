package ai.chat2db.gateway.start.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusClientConfig {

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClientV2(MilvusProperties properties) {
        ConnectConfig connectConfig = ConnectConfig.builder()
            .uri(properties.getUri())
            .build();
        return new MilvusClientV2(connectConfig);
    }
}
