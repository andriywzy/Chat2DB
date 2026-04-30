package ai.chat2db.server.domain.api.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Project {

    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private Long userId;

    private String name;

    private String description;

    private String scopeType;

    private Long scopeId;
}
