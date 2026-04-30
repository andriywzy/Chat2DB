package ai.chat2db.server.web.api.controller.ai.platform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamChunk {

    private String id;

    private String content;

    private Map<String, Object> metadata;
}
