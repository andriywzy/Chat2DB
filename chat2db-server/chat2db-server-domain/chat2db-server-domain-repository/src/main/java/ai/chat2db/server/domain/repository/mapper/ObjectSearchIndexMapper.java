package ai.chat2db.server.domain.repository.mapper;

import ai.chat2db.server.domain.repository.entity.ObjectSearchIndexDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;

public interface ObjectSearchIndexMapper extends BaseMapper<ObjectSearchIndexDO> {

    void batchInsert(List<ObjectSearchIndexDO> list);
}
