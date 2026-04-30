package ai.chat2db.server.web.api.controller.ai.platform.callback;

import ai.chat2db.server.web.api.controller.ai.platform.model.AiStreamChunk;

public interface AiStreamCallback {

    void onChunk(AiStreamChunk chunk);

    void onComplete();

    void onError(String errorMessage);
}
