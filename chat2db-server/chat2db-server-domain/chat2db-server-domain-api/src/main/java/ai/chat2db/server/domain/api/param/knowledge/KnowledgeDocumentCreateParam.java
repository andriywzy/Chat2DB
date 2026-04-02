package ai.chat2db.server.domain.api.param.knowledge;

import lombok.Data;

@Data
public class KnowledgeDocumentCreateParam {

    private String name;

    private String fileName;

    private String fileType;

    private String storagePath;

    private String status;

    private Integer sentenceCount;

    private Integer wordCount;

    private Integer vectorCount;

    private String contentPreview;

    private String errorMessage;
}
