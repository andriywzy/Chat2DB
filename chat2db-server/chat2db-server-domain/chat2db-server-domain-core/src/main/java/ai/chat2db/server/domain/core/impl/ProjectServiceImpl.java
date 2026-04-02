package ai.chat2db.server.domain.core.impl;

import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.project.ProjectCreateParam;
import ai.chat2db.server.domain.api.param.project.ProjectUpdateParam;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceDO;
import ai.chat2db.server.domain.repository.entity.ProjectDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceMapper;
import ai.chat2db.server.domain.repository.mapper.ProjectMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.util.ContextUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ProjectServiceImpl implements ProjectService {

    private ProjectMapper getMapper() {
        return Dbutils.getMapper(ProjectMapper.class);
    }

    private DataSourceMapper getDataSourceMapper() {
        return Dbutils.getMapper(DataSourceMapper.class);
    }

    @Override
    public DataResult<Long> create(ProjectCreateParam param) {
        ProjectDO data = new ProjectDO();
        data.setName(param.getName());
        data.setDescription(param.getDescription());
        data.setUserId(ContextUtils.getUserId());
        data.setScopeType(StringUtils.defaultIfBlank(param.getScopeType(), "USER"));
        data.setScopeId(param.getScopeId() == null ? ContextUtils.getUserId() : param.getScopeId());
        data.setGmtCreate(DateUtil.date());
        data.setGmtModified(DateUtil.date());
        getMapper().insert(data);
        return DataResult.of(data.getId());
    }

    @Override
    public DataResult<Long> update(ProjectUpdateParam param) {
        ProjectDO data = getMapper().selectById(param.getId());
        if (data == null) {
            throw new DataNotFoundException();
        }
        PermissionUtils.checkOperationPermission(data.getUserId());
        data.setName(param.getName());
        data.setDescription(param.getDescription());
        data.setGmtModified(DateUtil.date());
        getMapper().updateById(data);
        return DataResult.of(data.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        ProjectDO data = getMapper().selectById(id);
        if (data == null) {
            throw new DataNotFoundException();
        }
        PermissionUtils.checkOperationPermission(data.getUserId());

        LambdaUpdateWrapper<DataSourceDO> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(DataSourceDO::getProjectId, id)
            .set(DataSourceDO::getProjectId, null);
        getDataSourceMapper().update(null, updateWrapper);

        getMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<Project> queryList() {
        LambdaQueryWrapper<ProjectDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(ProjectDO::getGmtCreate, ProjectDO::getId);
        return ListResult.of(toModelList(getMapper().selectList(queryWrapper)));
    }

    @Override
    public DataResult<Project> query(Long id) {
        ProjectDO data = getMapper().selectById(id);
        if (data == null) {
            throw new DataNotFoundException();
        }
        return DataResult.of(toModel(data));
    }

    @Override
    public List<Project> queryList(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<ProjectDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ProjectDO::getId, ids);
        return toModelList(getMapper().selectList(queryWrapper));
    }

    private List<Project> toModelList(List<ProjectDO> list) {
        return list.stream().map(this::toModel).toList();
    }

    private Project toModel(ProjectDO data) {
        Project result = new Project();
        result.setId(data.getId());
        result.setUserId(data.getUserId());
        result.setName(data.getName());
        result.setDescription(data.getDescription());
        result.setScopeType(data.getScopeType());
        result.setScopeId(data.getScopeId());
        if (data.getGmtCreate() != null) {
            result.setGmtCreate(data.getGmtCreate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        if (data.getGmtModified() != null) {
            result.setGmtModified(data.getGmtModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return result;
    }
}
