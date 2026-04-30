package ai.chat2db.server.web.api.controller.ai.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderRequest {

    private List<AiChatMessage> messages;

    private String systemPrompt;

    private Boolean stream;

    private String model;

    private Double temperature;

    private Double topP;

    private Integer maxTokens;

    private Map<String, Object> metadata;
}
