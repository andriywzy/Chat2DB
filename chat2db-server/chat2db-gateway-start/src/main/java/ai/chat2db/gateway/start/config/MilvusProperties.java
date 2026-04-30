package ai.chat2db.gateway.start.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private String host = "milvus";

    private Integer port = 19530;

    private String knowledgeCollection = "chat2db_knowledge";

    private String schemaCollection = "chat2db_schema";

    private String metricType = "COSINE";

    private Integer topK = 5;

    private Integer varcharMaxLength = 65535;

    public String getUri() {
        return "http://" + host + ":" + port;
    }
}
