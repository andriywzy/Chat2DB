package ai.chat2db.server.web.api.controller.ai.platform.orchestrator;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.callback.SseEmitterAiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.config.AiConfigResolver;
import ai.chat2db.server.web.api.controller.ai.platform.context.AiContextService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiPromptBuilder;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProvider;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AiProviderRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultAiOrchestrator implements AiOrchestrator {

    private final AiConfigResolver aiConfigResolver;
    private final AiContextService aiContextService;
    private final AiPromptBuilder aiPromptBuilder;
    private final AiProviderRegistry providerRegistry;

    public DefaultAiOrchestrator(
        AiConfigResolver aiConfigResolver,
        AiContextService aiContextService,
        AiPromptBuilder aiPromptBuilder,
        AiProviderRegistry providerRegistry
    ) {
        this.aiConfigResolver = aiConfigResolver;
        this.aiContextService = aiContextService;
        this.aiPromptBuilder = aiPromptBuilder;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public SseEmitter streamChat(AiChatCommand command) throws IOException {
        AiSqlSourceEnum source = aiConfigResolver.getCurrentAiSqlSource();
        AiProvider provider = providerRegistry.get(source);
        String conversationKey = aiContextService.buildConversationKey(source, command.getUid());
        SseEmitter emitter = aiContextService.initializeEmitter(command.getEmitter(), conversationKey);

        String prompt = StringUtils.defaultIfBlank(
            command.getPromptOverride(),
            aiPromptBuilder.buildPrompt(command.getQueryRequest(), command.getRetrievalContext())
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

        provider.streamChat(
            AiProviderRequest.builder()
                .messages(messages)
                .stream(Boolean.TRUE)
                .metadata(metadata)
                .build(),
            new SseEmitterAiStreamCallback(emitter)
        );

        aiContextService.saveMessages(conversationKey, messages);
        return emitter;
    }
}
