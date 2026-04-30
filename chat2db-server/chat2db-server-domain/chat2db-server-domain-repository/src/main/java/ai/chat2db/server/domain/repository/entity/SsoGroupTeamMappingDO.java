package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("SSO_GROUP_TEAM_MAPPING")
public class SsoGroupTeamMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private String providerType;

    private String issuer;

    private String externalGroupCode;

    private Long teamId;

    private String syncMode;
}
