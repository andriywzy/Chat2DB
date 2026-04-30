package ai.chat2db.server.web.api.controller.ai.tongyi.client;

import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbedding;
import ai.chat2db.server.web.api.controller.ai.fastchat.embeddings.FastChatEmbeddingResponse;
import io.reactivex.Single;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface TongyiOpenAiApi {

    @POST("embeddings")
    Single<FastChatEmbeddingResponse> embeddings(@Body FastChatEmbedding embedding);
}
