package ai.chat2db.server.web.api.controller.ai.platform.callback;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiStreamChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SseEmitterAiStreamCallback implements AiStreamCallback {

    private final SseEmitter sseEmitter;

    @Override
    public void onChunk(AiStreamChunk chunk) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content", StringUtils.defaultString(chunk.getContent()));
            if (chunk.getMetadata() != null) {
                payload.putAll(chunk.getMetadata());
            }
            sseEmitter.send(SseEmitter.event()
                .id(chunk.getId())
                .data(payload)
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
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("content", StringUtils.EMPTY);
            payload.put("errorMessage", errorMessage);
            sseEmitter.send(SseEmitter.event()
                .id("[ERROR]")
                .data(payload)
                .reconnectTime(3000));
        } catch (Exception exception) {
            log.error("Failed to send AI error event", exception);
        } finally {
            onComplete();
        }
    }
}
