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
@TableName("OBJECT_SEARCH_INDEX")
public class ObjectSearchIndexDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "ID", type = IdType.AUTO)
    private Long id;

    private Date gmtCreate;

    private Date gmtModified;

    private Long dataSourceId;

    private String dataSourceNameSnapshot;

    private String databaseType;

    private String databaseName;

    private String schemaName;

    private String objectType;

    private String objectName;

    private String comment;

    private Long syncVersion;

    private String deleted;
}
