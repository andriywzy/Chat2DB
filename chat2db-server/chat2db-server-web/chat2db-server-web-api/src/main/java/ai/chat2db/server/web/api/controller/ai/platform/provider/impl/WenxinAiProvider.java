package ai.chat2db.server.web.api.controller.ai.platform.provider.impl;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.platform.callback.AiStreamCallback;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiProviderRequest;
import ai.chat2db.server.web.api.controller.ai.platform.provider.AbstractAiProvider;
import ai.chat2db.server.web.api.controller.ai.wenxin.client.WenxinAIClient;
import ai.chat2db.server.web.api.controller.ai.wenxin.listener.WenxinAIEventSourceListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WenxinAiProvider extends AbstractAiProvider {

    @Override
    public AiSqlSourceEnum getSource() {
        return AiSqlSourceEnum.WENXINAI;
    }

    @Override
    public void streamChat(AiProviderRequest request, AiStreamCallback callback) {
        List<FastChatMessage> messages = toFastChatMessages(request.getMessages());
        if (messages.size() >= 2 && messages.size() % 2 == 0) {
            messages.remove(messages.size() - 1);
        }
        WenxinAIClient.getInstance().streamCompletions(messages, new WenxinAIEventSourceListener(callback));
    }
}
