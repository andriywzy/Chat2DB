package ai.chat2db.server.web.api.controller.ai.platform.model;

import ai.chat2db.server.web.api.http.model.Knowledge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRetrievalContext {

    private List<String> schemaSnippets;

    private List<String> knowledgeSnippets;

    private List<Knowledge> knowledgeSources;

    public boolean hasSchemaSnippets() {
        return CollectionUtils.isNotEmpty(schemaSnippets);
    }

    public boolean hasKnowledgeSnippets() {
        return CollectionUtils.isNotEmpty(knowledgeSnippets);
    }

    public boolean hasKnowledgeSources() {
        return CollectionUtils.isNotEmpty(knowledgeSources);
    }
}
