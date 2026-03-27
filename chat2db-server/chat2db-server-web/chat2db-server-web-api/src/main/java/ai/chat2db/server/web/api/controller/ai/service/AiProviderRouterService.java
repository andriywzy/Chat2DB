package ai.chat2db.server.web.api.controller.ai.service;

import ai.chat2db.server.domain.api.enums.AiSqlSourceEnum;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.azure.client.AzureOpenAIClient;
import ai.chat2db.server.web.api.controller.ai.azure.listener.AzureOpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.azure.model.AzureChatMessage;
import ai.chat2db.server.web.api.controller.ai.baichuan.client.BaichuanAIClient;
import ai.chat2db.server.web.api.controller.ai.baichuan.listener.BaichuanChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.chat2db.client.Chat2dbAIClient;
import ai.chat2db.server.web.api.controller.ai.chat2db.listener.Chat2dbAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.claude.client.ClaudeAIClient;
import ai.chat2db.server.web.api.controller.ai.claude.listener.ClaudeAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatCompletionsOptions;
import ai.chat2db.server.web.api.controller.ai.claude.model.ClaudeChatMessage;
import ai.chat2db.server.web.api.controller.ai.fastchat.client.FastChatAIClient;
import ai.chat2db.server.web.api.controller.ai.fastchat.listener.FastChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.fastchat.model.FastChatMessage;
import ai.chat2db.server.web.api.controller.ai.openai.client.OpenAIClient;
import ai.chat2db.server.web.api.controller.ai.openai.listener.OpenAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.controller.ai.rest.client.RestAIClient;
import ai.chat2db.server.web.api.controller.ai.rest.listener.RestAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.tongyi.client.TongyiChatAIClient;
import ai.chat2db.server.web.api.controller.ai.tongyi.listener.TongyiChatAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.wenxin.client.WenxinAIClient;
import ai.chat2db.server.web.api.controller.ai.wenxin.listener.WenxinAIEventSourceListener;
import ai.chat2db.server.web.api.controller.ai.zhipu.client.ZhipuChatAIClient;
import ai.chat2db.server.web.api.controller.ai.zhipu.listener.ZhipuChatAIEventSourceListener;
import com.google.common.collect.Lists;
import com.unfbx.chatgpt.entity.chat.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AiProviderRouterService {

    private static final Integer MAX_PROMPT_LENGTH = 3850;

    private static final Integer TOKEN_CONVERT_CHAR_LENGTH = 4;

    @Value("${chatgpt.context.length}")
    private Integer contextLength;

    @Autowired
    private AiConfigurationService aiConfigurationService;

    @Autowired
    private AiPromptService aiPromptService;

    @Autowired
    private AiConversationService aiConversationService;

    public SseEmitter distributeAISql(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        AiSqlSourceEnum aiSqlSourceEnum = aiConfigurationService.getCurrentAiSqlSource();
        String scopedUid = aiConfigurationService.buildScopedUid(uid);
        switch (aiSqlSourceEnum) {
            case OPENAI:
                return chatWithOpenAi(queryRequest, sseEmitter, scopedUid);
            case CHAT2DBAI:
                return chatWithChat2dbAi(queryRequest, sseEmitter, scopedUid);
            case RESTAI:
                return chatWithRestAi(queryRequest, sseEmitter, scopedUid);
            case FASTCHATAI:
                return chatWithFastChatAi(queryRequest, sseEmitter, scopedUid);
            case AZUREAI:
                return chatWithAzureAi(queryRequest, sseEmitter, scopedUid);
            case CLAUDEAI:
                return chatWithClaudeAi(queryRequest, sseEmitter, scopedUid);
            case WENXINAI:
                return chatWithWenxinAi(queryRequest, sseEmitter, scopedUid);
            case BAICHUANAI:
                return chatWithBaichuanAi(queryRequest, sseEmitter, scopedUid);
            case TONGYIQIANWENAI:
                return chatWithTongyiChatAi(queryRequest, sseEmitter, scopedUid);
            case ZHIPUAI:
                return chatWithZhipuChatAi(queryRequest, sseEmitter, scopedUid);
            default:
                return chatWithOpenAi(queryRequest, sseEmitter, scopedUid);
        }
    }

    private SseEmitter chatWithRestAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        RestAIEventSourceListener restAIEventSourceListener = new RestAIEventSourceListener(sseEmitter);
        RestAIClient.getInstance().streamCompletions(messages, restAIEventSourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithOpenAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        validatePromptLength(prompt);

        List<Message> messages = new ArrayList<>();
        String normalizedPrompt = prompt.replaceAll("#", "");
        log.info(normalizedPrompt);
        Message currentMessage = Message.builder().content(normalizedPrompt).role(Message.Role.USER).build();
        messages.add(currentMessage);
        aiConversationService.buildSseEmitter(sseEmitter, uid);

        OpenAIEventSourceListener openAIEventSourceListener = new OpenAIEventSourceListener(sseEmitter);
        OpenAIClient.getInstance().streamChatCompletion(messages, openAIEventSourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithChat2dbAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        validatePromptLength(prompt);

        String normalizedPrompt = prompt.replaceAll("#", "");
        log.info(normalizedPrompt);
        Message currentMessage = Message.builder().content(normalizedPrompt).role(Message.Role.USER).build();
        List<Message> messages = new ArrayList<>();
        messages.add(currentMessage);
        aiConversationService.buildSseEmitter(sseEmitter, uid);

        Chat2dbAIEventSourceListener openAIEventSourceListener = new Chat2dbAIEventSourceListener(sseEmitter);
        Chat2dbAIClient.getInstance().streamCompletions(messages, openAIEventSourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithAzureAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        validatePromptLength(prompt);
        List<AzureChatMessage> messages = aiConversationService.buildAzureMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        AzureOpenAIEventSourceListener sourceListener = new AzureOpenAIEventSourceListener(sseEmitter);
        AzureOpenAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithFastChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        FastChatAIEventSourceListener sourceListener = new FastChatAIEventSourceListener(sseEmitter);
        FastChatAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithZhipuChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        ZhipuChatAIEventSourceListener sourceListener = new ZhipuChatAIEventSourceListener(sseEmitter);
        ZhipuChatAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithTongyiChatAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        TongyiChatAIEventSourceListener sourceListener = new TongyiChatAIEventSourceListener(sseEmitter);
        TongyiChatAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithBaichuanAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        BaichuanChatAIEventSourceListener sourceListener = new BaichuanChatAIEventSourceListener(sseEmitter);
        BaichuanAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithWenxinAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        List<FastChatMessage> messages = aiConversationService.buildFastChatMessages(uid, prompt, contextLength);
        if (messages.size() >= 2 && messages.size() % 2 == 0) {
            messages.remove(messages.size() - 1);
        }

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        WenxinAIEventSourceListener sourceListener = new WenxinAIEventSourceListener(sseEmitter);
        WenxinAIClient.getInstance().streamCompletions(messages, sourceListener);
        aiConversationService.saveMessages(uid, messages);
        return sseEmitter;
    }

    private SseEmitter chatWithClaudeAi(ChatQueryRequest queryRequest, SseEmitter sseEmitter, String uid) throws IOException {
        String prompt = aiPromptService.buildPrompt(queryRequest);
        ClaudeChatMessage claudeChatMessage = new ClaudeChatMessage();
        claudeChatMessage.setText(prompt);
        ClaudeChatCompletionsOptions chatCompletionsOptions = new ClaudeChatCompletionsOptions();
        chatCompletionsOptions.setPrompt(prompt);
        claudeChatMessage.setCompletion(chatCompletionsOptions);

        aiConversationService.buildSseEmitter(sseEmitter, uid);

        ClaudeAIEventSourceListener sourceListener = new ClaudeAIEventSourceListener(sseEmitter);
        ClaudeAIClient.getInstance().streamCompletions(claudeChatMessage, sourceListener);
        return sseEmitter;
    }

    private void validatePromptLength(String prompt) {
        if (prompt.length() / TOKEN_CONVERT_CHAR_LENGTH > MAX_PROMPT_LENGTH) {
            log.error("The prompt exceeds the maximum length: {}, input length: {}, please re-enter", MAX_PROMPT_LENGTH,
                prompt.length() / TOKEN_CONVERT_CHAR_LENGTH);
            throw new ParamBusinessException();
        }
    }
}
