package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import ai.chat2db.server.web.api.controller.ai.rest.client.RestAIClient;
import ai.chat2db.server.web.api.controller.ai.rest.listener.RestAIEventSourceListener;
import org.springframework.stereotype.Component;

@Component
public class RestAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.RESTAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        RestAIClient.getInstance().streamCompletions(
            toFastChatMessages(request.getMessages()),
            new RestAIEventSourceListener(callback)
        );
    }
}
