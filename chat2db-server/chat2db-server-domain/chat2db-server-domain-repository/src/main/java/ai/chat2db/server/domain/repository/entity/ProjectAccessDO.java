package ai.chat2db.server.domain.repository.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("PROJECT_ACCESS")
public class ProjectAccessDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long projectId;

    private String accessObjectType;

    private Long accessObjectId;

    private String permissionType;
}
