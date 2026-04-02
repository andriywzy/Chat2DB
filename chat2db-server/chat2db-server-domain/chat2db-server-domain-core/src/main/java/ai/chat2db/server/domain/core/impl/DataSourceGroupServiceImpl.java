package ai.chat2db.server.domain.core.impl;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSourceGroup;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupUpdateParam;
import ai.chat2db.server.domain.api.param.project.ProjectCreateParam;
import ai.chat2db.server.domain.api.param.project.ProjectUpdateParam;
import ai.chat2db.server.domain.api.service.DataSourceGroupService;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DataSourceGroupServiceImpl implements DataSourceGroupService {

    @Autowired
    private ProjectService projectService;

    @Override
    public DataResult<Long> create(DataSourceGroupCreateParam param) {
        ProjectCreateParam projectCreateParam = new ProjectCreateParam();
        projectCreateParam.setName(param.getName());
        return projectService.create(projectCreateParam);
    }

    @Override
    public DataResult<Long> update(DataSourceGroupUpdateParam param) {
        ProjectUpdateParam projectUpdateParam = new ProjectUpdateParam();
        projectUpdateParam.setId(param.getId());
        projectUpdateParam.setName(param.getName());
        return projectService.update(projectUpdateParam);
    }

    @Override
    public ActionResult delete(Long id) {
        return projectService.delete(id);
    }

    @Override
    public ListResult<DataSourceGroup> queryList() {
        return ListResult.of(toGroupList(projectService.queryList().getData()));
    }

    @Override
    public DataResult<DataSourceGroup> query(Long id) {
        return DataResult.of(toGroup(projectService.query(id).getData()));
    }

    @Override
    public List<DataSourceGroup> queryList(List<Long> ids) {
        return toGroupList(projectService.queryList(ids));
    }

    private List<DataSourceGroup> toGroupList(List<Project> list) {
        return list.stream().map(this::toGroup).toList();
    }

    private DataSourceGroup toGroup(Project data) {
        DataSourceGroup result = new DataSourceGroup();
        result.setId(data.getId());
        result.setUserId(data.getUserId());
        result.setName(data.getName());
        result.setGmtCreate(data.getGmtCreate());
        result.setGmtModified(data.getGmtModified());
        return result;
    }
}
