package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.claude.client.ClaudeAIClient;
import ai.chat2db.server.web.api.controller.ai.claude.listener.ClaudeAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

@Component
public class ClaudeAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.CLAUDEAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        String prompt = CollectionUtils.isEmpty(request.getMessages())
            ? ""
            : request.getMessages().get(request.getMessages().size() - 1).getContent();
        ClaudeChatCompletionsOptions options = new ClaudeChatCompletionsOptions();
        options.setPrompt(prompt);
        ClaudeChatMessage message = new ClaudeChatMessage();
        message.setText(prompt);
        message.setCompletion(options);
        ClaudeAIClient.getInstance().streamCompletions(message, new ClaudeAIEventSourceListener(callback));
    }

    @Override
    public boolean supportsConversationContext() {
        return false;
    }
}
