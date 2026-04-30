package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.baichuan.client.BaichuanAIClient;
import ai.chat2db.server.web.api.controller.ai.baichuan.listener.BaichuanChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import org.springframework.stereotype.Component;

@Component
public class BaichuanAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.BAICHUANAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        BaichuanAIClient.getInstance().streamCompletions(
            toFastChatMessages(request.getMessages()),
            new BaichuanChatAIEventSourceListener(callback)
        );
    }
}
