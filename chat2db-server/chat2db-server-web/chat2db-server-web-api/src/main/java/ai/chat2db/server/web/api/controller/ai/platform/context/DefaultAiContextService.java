package ai.chat2db.server.web.api.controller.ai.platform.context;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.web.api.controller.ai.config.LocalCache;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatMessage;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DefaultAiContextService implements AiContextService {

    @Override
    public SseEmitter initializeEmitter(SseEmitter emitter, String conversationKey) throws IOException {
        emitter.send(SseEmitter.event()
            .id(conversationKey)
            .name("connect successfully！！！！")
            .data(LocalDateTime.now())
            .reconnectTime(3000));
        emitter.onCompletion(() -> log.info("{}, conversation#{} on completion", LocalDateTime.now(), conversationKey));
        emitter.onTimeout(() -> log.info("{}, conversation#{} on timeout#{}", LocalDateTime.now(), conversationKey, emitter.getTimeout()));
        emitter.onError(throwable -> log.info("{}, conversation#{} on error#{}", LocalDateTime.now(), conversationKey, throwable));
        return emitter;
    }

    @Override
    public List<AiChatMessage> buildMessages(String conversationKey, String prompt, Integer contextLength, boolean withHistory) {
        List<AiChatMessage> messages = withHistory ? getCachedMessages(conversationKey) : Lists.newArrayList();
        if (CollectionUtils.isNotEmpty(messages) && messages.size() >= contextLength) {
            messages = Lists.newArrayList(messages.subList(1, contextLength));
        } else if (CollectionUtils.isEmpty(messages)) {
            messages = Lists.newArrayList();
        }
        messages.add(AiChatMessage.builder().role("user").content(prompt).build());
        return messages;
    }

    @Override
    public void saveMessages(String conversationKey, List<AiChatMessage> messages) {
        LocalCache.CACHE.put(conversationKey, messages, LocalCache.TIMEOUT);
    }

    @Override
    public String buildConversationKey(AiSqlSourceEnum source, String uid) {
        return source.getCode() + uid;
    }

    private List<AiChatMessage> getCachedMessages(String conversationKey) {
        Object cached = LocalCache.CACHE.get(conversationKey);
        List<AiChatMessage> result = new ArrayList<>();
        if (cached instanceof String) {
            List<AiChatMessage> parsed = JSON.parseArray((String) cached, AiChatMessage.class);
            if (CollectionUtils.isNotEmpty(parsed)) {
                result.addAll(parsed);
            }
            return result;
        }
        if (cached instanceof List<?>) {
            for (Object item : (List<?>) cached) {
                if (item instanceof AiChatMessage message) {
                    result.add(message);
                }
            }
        }
        return result;
    }
}
