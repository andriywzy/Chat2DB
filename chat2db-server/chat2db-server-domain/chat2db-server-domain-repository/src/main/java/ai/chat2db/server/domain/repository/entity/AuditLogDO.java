package ai.chat2db.server.domain.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("AUDIT_LOG")
public class AuditLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;
    private Date gmtCreate;
    private Date gmtModified;
    private String category;
    private String actionType;
    private String resourceType;
    private Long operatorUserId;
    private String operatorUserName;
    private String roleCode;
    private String targetId;
    private String targetName;
    private String requestPath;
    private String requestMethod;
    private String requestId;
    private String clientIp;
    private String userAgent;
    private String status;
    private String detailSummary;
    private String detailPayload;
    private String errorMessage;
}
