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
@TableName("OBJECT_SEARCH_SYNC_STATUS")
public class ObjectSearchSyncStatusDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long dataSourceId;

    private String lastSyncStatus;

    private Date lastSyncTime;

    private String lastSyncError;

    private Long lastSyncVersion;

    private Date nextSyncTime;
}
