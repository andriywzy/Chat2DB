package ai.chat2db.server.web.start.controller.sso.vo;

import ai.chat2db.server.admin.api.controller.team.vo.SimpleTeamVO;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SsoGroupMappingVO {

    private Long id;

    private String providerType;

    private String issuer;

    private String externalGroupCode;

    private String syncMode;

    private SimpleTeamVO team;

    private LocalDateTime gmtModified;
}
