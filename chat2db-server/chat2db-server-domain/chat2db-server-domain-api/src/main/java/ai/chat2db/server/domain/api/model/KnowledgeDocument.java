package ai.chat2db.server.domain.api.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocument {

    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Long userId;

    private String name;

    private String fileName;

    private String fileType;

    private String status;

    private Integer sentenceCount;

    private Integer wordCount;

    private Integer vectorCount;

    private String contentPreview;

    private String errorMessage;
}
