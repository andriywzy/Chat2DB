package ai.chat2db.server.domain.core.impl;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import ai.chat2db.server.domain.api.model.DataSourceGroup;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupUpdateParam;
import ai.chat2db.server.domain.api.service.DataSourceGroupService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceGroupDO;
import ai.chat2db.server.domain.repository.entity.DataSourceGroupMappingDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceGroupMapper;
import ai.chat2db.server.domain.repository.mapper.DataSourceGroupMappingMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
public class DataSourceGroupServiceImpl implements DataSourceGroupService {

    private DataSourceGroupMapper getMapper() {
        return Dbutils.getMapper(DataSourceGroupMapper.class);
    }

    private DataSourceGroupMappingMapper getGroupMappingMapper() {
        return Dbutils.getMapper(DataSourceGroupMappingMapper.class);
    }

    @Override
    public DataResult<Long> create(DataSourceGroupCreateParam param) {
        DataSourceGroupDO data = new DataSourceGroupDO();
        data.setName(param.getName());
        data.setUserId(ContextUtils.getUserId());
        data.setGmtCreate(DateUtil.date());
        data.setGmtModified(DateUtil.date());
        getMapper().insert(data);
        return DataResult.of(data.getId());
    }

    @Override
    public DataResult<Long> update(DataSourceGroupUpdateParam param) {
        LambdaQueryWrapper<DataSourceGroupDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DataSourceGroupDO::getId, param.getId());
        queryWrapper.eq(DataSourceGroupDO::getUserId, ContextUtils.getUserId());
        DataSourceGroupDO data = getMapper().selectOne(queryWrapper);
        if (data == null) {
            throw new DataNotFoundException();
        }
        data.setName(param.getName());
        data.setGmtModified(DateUtil.date());
        getMapper().updateById(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        LambdaQueryWrapper<DataSourceGroupDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DataSourceGroupDO::getId, id);
        queryWrapper.eq(DataSourceGroupDO::getUserId, ContextUtils.getUserId());
        DataSourceGroupDO data = getMapper().selectOne(queryWrapper);
        if (data == null) {
            throw new DataNotFoundException();
        }

        LambdaQueryWrapper<DataSourceGroupMappingDO> mappingQueryWrapper = new LambdaQueryWrapper<>();
        mappingQueryWrapper.eq(DataSourceGroupMappingDO::getUserId, ContextUtils.getUserId());
        mappingQueryWrapper.eq(DataSourceGroupMappingDO::getGroupId, id);
        getGroupMappingMapper().delete(mappingQueryWrapper);

        getMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<DataSourceGroup> queryCurrentUserList() {
        LambdaQueryWrapper<DataSourceGroupDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DataSourceGroupDO::getUserId, ContextUtils.getUserId());
        queryWrapper.orderByAsc(DataSourceGroupDO::getGmtCreate, DataSourceGroupDO::getId);
        return ListResult.of(toModelList(getMapper().selectList(queryWrapper)));
    }

    @Override
    public DataResult<DataSourceGroup> queryCurrentUserGroup(Long id) {
        LambdaQueryWrapper<DataSourceGroupDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DataSourceGroupDO::getId, id);
        queryWrapper.eq(DataSourceGroupDO::getUserId, ContextUtils.getUserId());
        DataSourceGroupDO data = getMapper().selectOne(queryWrapper);
        if (data == null) {
            throw new DataNotFoundException();
        }
        return DataResult.of(toModel(data));
    }

    @Override
    public List<DataSourceGroup> queryCurrentUserList(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<DataSourceGroupDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(DataSourceGroupDO::getId, ids);
        queryWrapper.eq(DataSourceGroupDO::getUserId, ContextUtils.getUserId());
        return toModelList(getMapper().selectList(queryWrapper));
    }

    private List<DataSourceGroup> toModelList(List<DataSourceGroupDO> list) {
        return list.stream().map(this::toModel).toList();
    }

    private DataSourceGroup toModel(DataSourceGroupDO data) {
        DataSourceGroup result = new DataSourceGroup();
        result.setId(data.getId());
        result.setUserId(data.getUserId());
        result.setName(data.getName());
        if (data.getGmtCreate() != null) {
            result.setGmtCreate(data.getGmtCreate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (data.getGmtModified() != null) {
            result.setGmtModified(data.getGmtModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return result;
    }
}
