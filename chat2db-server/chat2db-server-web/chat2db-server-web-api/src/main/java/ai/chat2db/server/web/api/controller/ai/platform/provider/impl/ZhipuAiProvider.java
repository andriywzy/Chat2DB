package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import ai.chat2db.server.web.api.controller.ai.zhipu.client.ZhipuChatAIClient;
import ai.chat2db.server.web.api.controller.ai.zhipu.listener.ZhipuChatAIEventSourceListener;
import org.springframework.stereotype.Component;

@Component
public class ZhipuAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.ZHIPUAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        ZhipuChatAIClient.getInstance().streamCompletions(
            toFastChatMessages(request.getMessages()),
            new ZhipuChatAIEventSourceListener(callback)
        );
    }
}
