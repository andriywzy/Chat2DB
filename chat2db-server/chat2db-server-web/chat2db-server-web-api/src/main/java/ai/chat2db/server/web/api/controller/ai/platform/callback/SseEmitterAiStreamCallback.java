package ai.chat2db.server.web.api.controller.ai.platform.callback;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiStreamChunk;
import com.unfbx.chatgpt.entity.chat.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RequiredArgsConstructor
public class SseEmitterAiStreamCallback implements AiStreamCallback {

    private final SseEmitter sseEmitter;

    @Override
    public void onChunk(AiStreamChunk chunk) {
        try {
            Message message = new Message();
            message.setContent(chunk.getContent());
            sseEmitter.send(SseEmitter.event()
                .id(chunk.getId())
                .data(message)
                .reconnectTime(3000));
        } catch (Exception exception) {
            log.error("Failed to stream AI chunk", exception);
            throw new IllegalStateException("Failed to stream AI chunk", exception);
        }
    }

    @Override
    public void onComplete() {
        try {
            sseEmitter.send(SseEmitter.event()
                .id("[DONE]")
                .data("[DONE]")
                .reconnectTime(3000));
        } catch (Exception exception) {
            log.error("Failed to send AI completion event", exception);
        } finally {
            sseEmitter.complete();
        }
    }

    @Override
    public void onError(String errorMessage) {
        try {
            Message message = new Message();
            message.setContent(errorMessage);
            sseEmitter.send(SseEmitter.event()
                .id("[ERROR]")
                .data(message)
                .reconnectTime(3000));
        } catch (Exception exception) {
            log.error("Failed to send AI error event", exception);
        } finally {
            onComplete();
        }
    }
}
