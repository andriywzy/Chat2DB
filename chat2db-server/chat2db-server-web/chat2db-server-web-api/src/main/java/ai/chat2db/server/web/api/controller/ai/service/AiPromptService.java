package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.embedding.AiEmbeddingService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiPromptBuilder;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiSchemaContextService;
import ai.chat2db.server.web.api.controller.ai.platform.retrieval.AiRetrievalService;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatAIClient;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import org.springframework.stereotype.Service;

@Service
public class AiPromptService {

    private final AiPromptBuilder aiPromptBuilder;
    private final AiSchemaContextService aiSchemaContextService;
    private final AiRetrievalService aiRetrievalService;
    private final AiEmbeddingService aiEmbeddingService;
    private final AiConfigurationService aiConfigurationService;

    public AiPromptService(
        AiPromptBuilder aiPromptBuilder,
        AiSchemaContextService aiSchemaContextService,
        AiRetrievalService aiRetrievalService,
        AiEmbeddingService aiEmbeddingService,
        AiConfigurationService aiConfigurationService
    ) {
        this.aiPromptBuilder = aiPromptBuilder;
        this.aiSchemaContextService = aiSchemaContextService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiEmbeddingService = aiEmbeddingService;
        this.aiConfigurationService = aiConfigurationService;
    }

    public String buildPrompt(ChatQueryRequest queryRequest) {
        return aiPromptBuilder.buildPrompt(queryRequest);
    }

    public String getApiKey() {
        return aiConfigurationService.getChat2dbApiKey();
    }

    public String queryDatabaseType(ChatQueryRequest queryRequest) {
        return aiSchemaContextService.queryDatabaseType(queryRequest);
    }

    public String mappingDatabaseSchema(ChatQueryRequest queryRequest) {
        return queryDatabaseSchema(queryRequest);
    }

    public String queryDatabaseSchema(ChatQueryRequest queryRequest) {
        AiRetrievalContext context = aiRetrievalService.retrieveSchema(AiRetrievalQuery.builder()
            .dataSourceId(queryRequest.getDataSourceId())
            .databaseName(queryRequest.getDatabaseName())
            .schemaName(queryRequest.getSchemaName())
            .message(queryRequest.getMessage())
            .build());
        return context == null || context.getSchemaSnippets() == null ? "" : com.alibaba.fastjson2.JSON.toJSONString(context.getSchemaSnippets());
    }

    public String querySchemaByEs(ChatQueryRequest queryRequest) {
        return "";
    }

    public FastChatEmbeddingResponse distributeAIEmbedding(String input) {
        AiEmbeddingResponse response = aiEmbeddingService.embed(input);
        if (response == null || response.getVectors() == null) {
            return null;
        }
        FastChatEmbeddingResponse result = new FastChatEmbeddingResponse();
        result.setData(response.getVectors().stream().map(vector -> {
            ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatItem item =
                new ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatItem();
            item.setEmbedding(vector);
            return item;
        }).toList());
        return result;
    }
}
