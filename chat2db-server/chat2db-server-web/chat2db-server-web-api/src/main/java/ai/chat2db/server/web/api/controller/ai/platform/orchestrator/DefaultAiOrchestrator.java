package ai.chat2db.server.web.api.controller.ai.platform.orchestrator;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.config.AiConfigResolver;
import ai.chat2db.server.web.api.controller.ai.platform.context.AiContextService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiStreamChunk;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiSchemaContextService;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiPromptBuilder;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProvider;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProviderRegistry;
import ai.chat2db.server.web.api.controller.ai.platform.retrieval.AiRetrievalService;
import ai.chat2db.server.web.api.controller.ai.platform.sql.SqlOnlyResponseSanitizer;
import ai.chat2db.server.web.api.controller.ai.platform.sql.SqlOnlyResultValidator;
import ai.chat2db.server.web.api.controller.ai.platform.sql.SqlOnlyValidationResult;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultAiOrchestrator implements AiOrchestrator {

    private final AiConfigResolver aiConfigResolver;
    private final AiContextService aiContextService;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiProviderRegistry providerRegistry;
    private final AiRetrievalService aiRetrievalService;
    private final AiSchemaContextService aiSchemaContextService;
    private final SqlOnlyResponseSanitizer sqlOnlyResponseSanitizer;
    private final SqlOnlyResultValidator sqlOnlyResultValidator;

    public DefaultAiOrchestrator(
        AiConfigResolver aiConfigResolver,
        AiContextService aiContextService,
        AiPromptBuilder aiPromptBuilder,
        AiProviderRegistry providerRegistry,
        AiRetrievalService aiRetrievalService,
        AiSchemaContextService aiSchemaContextService,
        SqlOnlyResponseSanitizer sqlOnlyResponseSanitizer,
        SqlOnlyResultValidator sqlOnlyResultValidator
    ) {
        this.aiConfigResolver = aiConfigResolver;
        this.aiContextService = aiContextService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.providerRegistry = providerRegistry;
        this.aiRetrievalService = aiRetrievalService;
        this.aiSchemaContextService = aiSchemaContextService;
        this.sqlOnlyResponseSanitizer = sqlOnlyResponseSanitizer;
        this.sqlOnlyResultValidator = sqlOnlyResultValidator;
    }

    @Override
    public SseEmitter streamChat(AiChatCommand command) throws IOException {
        AiSqlSourceEnum source = aiConfigResolver.getCurrentAiSqlSource();
        AiProvider provider = providerRegistry.get(source);
        String conversationKey = aiContextService.buildConversationKey(source, command.getUid());
        SseEmitter emitter = aiContextService.initializeEmitter(command.getEmitter(), conversationKey);

        AiRetrievalContext retrievalContext = resolveRetrievalContext(command);
        String prompt = StringUtils.defaultIfBlank(
            command.getPromptOverride(),
            aiPromptBuilder.buildPrompt(command.getQueryRequest(), retrievalContext)
        );
        provider.validatePrompt(prompt);

        List<AiChatMessage> messages = aiContextService.buildMessages(
            conversationKey,
            prompt,
            aiConfigResolver.getContextLength(),
            provider.supportsConversationContext()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source.getCode());
        metadata.put("conversationKey", conversationKey);
        if (retrievalContext != null && retrievalContext.hasSchemaSources()) {
            metadata.put("schemaSources", retrievalContext.getSchemaSources());
        }

        AiProviderRequest providerRequest = AiProviderRequest.builder()
            .messages(messages)
            .stream(Boolean.TRUE)
            .metadata(metadata)
            .build();
        SseEmitterAiStreamCallback emitterCallback = new SseEmitterAiStreamCallback(emitter);
        if (command.getQueryRequest().isSqlOnlyMode()) {
            Set<String> availableTables = sqlOnlyResultValidator.preloadAvailableTables(command.getQueryRequest());
            streamSqlOnly(
                provider,
                providerRequest,
                emitterCallback,
                prompt,
                command.getQueryRequest(),
                metadata,
                availableTables,
                0
            );
        } else {
            provider.streamChat(providerRequest, emitterCallback);
        }

        aiContextService.saveMessages(conversationKey, messages);
        return emitter;
    }

    private void streamSqlOnly(
        AiProvider provider,
        AiProviderRequest request,
        SseEmitterAiStreamCallback emitterCallback,
        String prompt,
        ChatQueryRequest queryRequest,
        Map<String, Object> responseMetadata,
        Set<String> availableTables,
        int retryCount
    ) {
        StringBuilder buffer = new StringBuilder();
        provider.streamChat(request, new ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback() {
            @Override
            public void onChunk(AiStreamChunk chunk) {
                buffer.append(StringUtils.defaultString(chunk.getContent()));
            }

            @Override
            public void onComplete() {
                String sanitized = sqlOnlyResponseSanitizer.sanitize(buffer.toString());
                if (StringUtils.isNotBlank(sanitized)) {
                    SqlOnlyValidationResult validationResult = sqlOnlyResultValidator.validate(sanitized, availableTables);
                    if (!validationResult.isValid()) {
                        if (retryCount < 1) {
                            String retryPrompt = sqlOnlyResponseSanitizer.buildRetryPrompt(prompt) + "\n" + validationResult.getMessage();
                            streamSqlOnly(
                                provider,
                                rebuildSqlOnlyRequest(request, retryPrompt),
                                emitterCallback,
                                retryPrompt,
                                queryRequest,
                                responseMetadata,
                                availableTables,
                                retryCount + 1
                            );
                            return;
                        }
                        responseMetadata.put("validationWarning", validationResult.getMessage());
                    }
                    emitterCallback.onChunk(AiStreamChunk.builder()
                        .content(sanitized)
                        .metadata(withResponseModeMetadata(responseMetadata))
                        .build());
                    emitterCallback.onComplete();
                    return;
                }
                if (retryCount < 1) {
                    String retryPrompt = sqlOnlyResponseSanitizer.buildRetryPrompt(prompt);
                    streamSqlOnly(
                        provider,
                        rebuildSqlOnlyRequest(request, retryPrompt),
                        emitterCallback,
                        retryPrompt,
                        queryRequest,
                        responseMetadata,
                        availableTables,
                        retryCount + 1
                    );
                    return;
                }
                emitterCallback.onChunk(AiStreamChunk.builder()
                    .content(StringUtils.EMPTY)
                    .metadata(Map.of(
                        "errorCode", "AI_SQL_ONLY_INVALID",
                        "errorMessage", "AI could not produce a valid SQL statement. Please refine the requirement and try again."
                    ))
                    .build());
                emitterCallback.onComplete();
            }

            @Override
            public void onError(String errorMessage) {
                if (retryCount < 1) {
                    String retryPrompt = sqlOnlyResponseSanitizer.buildRetryPrompt(prompt);
                    streamSqlOnly(
                        provider,
                        rebuildSqlOnlyRequest(request, retryPrompt),
                        emitterCallback,
                        retryPrompt,
                        queryRequest,
                        responseMetadata,
                        availableTables,
                        retryCount + 1
                    );
                    return;
                }
                emitterCallback.onChunk(AiStreamChunk.builder()
                    .content(StringUtils.EMPTY)
                    .metadata(Map.of(
                        "errorCode", "AI_SQL_ONLY_FAILED",
                        "errorMessage", StringUtils.defaultIfBlank(errorMessage, "SQL generation failed.")
                    ))
                    .build());
                emitterCallback.onComplete();
            }
        });
    }

    private Map<String, Object> withResponseModeMetadata(Map<String, Object> metadata) {
        Map<String, Object> payload = new HashMap<>(metadata);
        payload.put("responseMode", "SQL_ONLY");
        return payload;
    }

    private AiRetrievalContext resolveRetrievalContext(AiChatCommand command) {
        if (command.getRetrievalContext() != null) {
            return command.getRetrievalContext();
        }
        ChatQueryRequest queryRequest = command.getQueryRequest();
        if (queryRequest == null || !PromptType.NL_2_SQL.getCode().equals(queryRequest.getPromptType())) {
            return null;
        }
        if (CollectionUtils.isNotEmpty(queryRequest.getTableNames())) {
            return aiSchemaContextService.buildSelectedSchemaContext(queryRequest);
        }
        return aiRetrievalService.retrieveSchema(AiRetrievalQuery.builder()
            .dataSourceId(queryRequest.getDataSourceId())
            .databaseName(queryRequest.getDatabaseName())
            .schemaName(queryRequest.getSchemaName())
            .message(queryRequest.getMessage())
            .refresh(queryRequest.isRefresh())
            .build());
    }

    private AiProviderRequest rebuildSqlOnlyRequest(AiProviderRequest originalRequest, String retryPrompt) {
        List<AiChatMessage> retryMessages = originalRequest.getMessages().stream()
            .map(message -> AiChatMessage.builder()
                .role(message.getRole())
                .content(message.getContent())
                .build())
            .toList();
        if (!retryMessages.isEmpty()) {
            AiChatMessage lastMessage = retryMessages.get(retryMessages.size() - 1);
            lastMessage.setContent(retryPrompt);
        }
        return AiProviderRequest.builder()
            .messages(retryMessages)
            .systemPrompt(originalRequest.getSystemPrompt())
            .stream(originalRequest.getStream())
            .model(originalRequest.getModel())
            .temperature(originalRequest.getTemperature())
            .topP(originalRequest.getTopP())
            .maxTokens(originalRequest.getMaxTokens())
            .metadata(originalRequest.getMetadata())
            .build();
    }
}
