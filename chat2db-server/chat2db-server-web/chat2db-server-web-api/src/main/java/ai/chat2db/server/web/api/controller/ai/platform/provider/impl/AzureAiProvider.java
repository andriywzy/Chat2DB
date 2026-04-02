package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.azure.client.AzureOpenAIClient;
import ai.chat2db.server.web.api.controller.ai.azure.listener.AzureOpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.springframework.stereotype.Component;

@Component
public class AzureAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.AZUREAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        AzureOpenAIClient.getInstance().streamCompletions(
            toAzureMessages(request.getMessages()),
            new AzureOpenAIEventSourceListener(callback)
        );
    }
}
