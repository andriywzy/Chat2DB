package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatRole;
import ai.chat2db.server.web.api.controller.ai.config.LocalCache;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatRole;
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
public class AiConversationService {

    public SseEmitter buildSseEmitter(SseEmitter sseEmitter, String uid) throws IOException {
        sseEmitter.send(SseEmitter.event().id(uid).name("connect successfully！！！！").data(LocalDateTime.now()).reconnectTime(3000));
        sseEmitter.onCompletion(() -> log.info(LocalDateTime.now() + ", uid#" + uid + ", on completion"));
        sseEmitter.onTimeout(
            () -> log.info(LocalDateTime.now() + ", uid#" + uid + ", on timeout#" + sseEmitter.getTimeout()));
        sseEmitter.onError(
            throwable -> {
                try {
                    log.info(LocalDateTime.now() + ", uid#" + uid + ", on error#" + throwable);
                    sseEmitter.send(SseEmitter.event().id("765431").name("An exception occurs!").data(throwable.getMessage())
                        .reconnectTime(3000));
                } catch (IOException e) {
                    log.error("An exception occurs!{}", e.getMessage(), e);
                }
            }
        );
        return sseEmitter;
    }

    public List<FastChatMessage> buildFastChatMessages(String uid, String prompt, Integer contextLength) {
        List<FastChatMessage> messages = getCachedMessages(uid, FastChatMessage.class);
        if (CollectionUtils.isNotEmpty(messages) && messages.size() >= contextLength) {
            messages = Lists.newArrayList(messages.subList(1, contextLength));
        } else if (CollectionUtils.isEmpty(messages)) {
            messages = Lists.newArrayList();
        }
        FastChatMessage currentMessage = new FastChatMessage(FastChatRole.USER).setContent(prompt);
        messages.add(currentMessage);
        return messages;
    }

    public List<AzureChatMessage> buildAzureMessages(String uid, String prompt, Integer contextLength) {
        List<AzureChatMessage> messages = getCachedMessages(uid, AzureChatMessage.class);
        if (CollectionUtils.isNotEmpty(messages) && messages.size() >= contextLength) {
            messages = Lists.newArrayList(messages.subList(1, contextLength));
        } else if (CollectionUtils.isEmpty(messages)) {
            messages = Lists.newArrayList();
        }
        AzureChatMessage currentMessage = new AzureChatMessage(AzureChatRole.USER).setContent(prompt);
        messages.add(currentMessage);
        return messages;
    }

    public void saveMessages(String uid, Object messages) {
        LocalCache.CACHE.put(uid, messages, LocalCache.TIMEOUT);
    }

    private <T> List<T> getCachedMessages(String uid, Class<T> clazz) {
        Object cached = LocalCache.CACHE.get(uid);
        List<T> result = new ArrayList<>();
        if (cached instanceof String) {
            List<T> parsed = JSON.parseArray((String) cached, clazz);
            if (CollectionUtils.isNotEmpty(parsed)) {
                result.addAll(parsed);
            }
            return result;
        }
        if (cached instanceof List<?>) {
            for (Object item : (List<?>) cached) {
                if (clazz.isInstance(item)) {
                    result.add(clazz.cast(item));
                }
            }
        }
        return result;
    }
}
