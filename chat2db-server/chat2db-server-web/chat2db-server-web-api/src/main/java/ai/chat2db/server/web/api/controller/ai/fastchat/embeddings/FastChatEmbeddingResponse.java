package ai.chat2db.server.web.api.controller.ai.fastchat.embeddings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.unfbx.chatgpt.entity.common.Usage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * description：
 *
 * @author https:www.unfbx.com
 *  2023-02-15
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FastChatEmbeddingResponse implements Serializable {

    private String object;
    private List<FastChatItem> data;
    private String model;
    private Usage usage;
}
