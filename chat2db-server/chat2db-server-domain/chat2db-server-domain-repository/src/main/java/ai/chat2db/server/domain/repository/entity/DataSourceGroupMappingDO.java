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
@TableName("DATA_SOURCE_GROUP_MAPPING")
public class DataSourceGroupMappingDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long userId;

    private Long groupId;

    private Long dataSourceId;
}
