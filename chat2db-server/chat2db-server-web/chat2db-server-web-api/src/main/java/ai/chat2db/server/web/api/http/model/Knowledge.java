package ai.chat2db.server.web.api.http.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Knowledge {

    private Long id;

    private Long documentId;

    private String documentName;

    private String fileType;

    private String content;

    private String contentVector;

    private Integer wordCount;

    private Float score;

    public Knowledge(Long id, Long documentId, String content, Integer wordCount, Float score) {
        this.id = id;
        this.documentId = documentId;
        this.content = content;
        this.wordCount = wordCount;
        this.score = score;
    }
}
