package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("KNOWLEDGE_DOCUMENT")
public class KnowledgeDocumentDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
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
