package ai.chat2db.server.web.api.controller.ai.platform.callback;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiStreamChunk;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractAiEventSourceListener extends EventSourceListener {

    protected final AiStreamCallback callback;

    protected final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final AtomicBoolean finished = new AtomicBoolean(false);

    protected AbstractAiEventSourceListener(AiStreamCallback callback) {
        this.callback = callback;
    }

    protected void sendChunk(String id, String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        callback.onChunk(AiStreamChunk.builder().id(id).content(content).build());
    }

    protected void complete() {
        if (finished.compareAndSet(false, true)) {
            callback.onComplete();
        }
    }

    protected void fail(String message) {
        if (finished.compareAndSet(false, true)) {
            callback.onError(message);
        }
    }

    protected String resolveFailureBody(Throwable t, Response response) throws Exception {
        if (Objects.isNull(response)) {
            return t == null ? "Unknown AI error" : t.getMessage();
        }
        ResponseBody body = response.body();
        String bodyString = t != null ? t.getMessage() : "";
        if (Objects.nonNull(body)) {
            bodyString = body.string();
            if (StringUtils.isBlank(bodyString) && Objects.nonNull(t)) {
                bodyString = t.getMessage();
            }
        }
        return bodyString;
    }

    @Override
    public void onClosed(EventSource eventSource) {
        complete();
    }
}
