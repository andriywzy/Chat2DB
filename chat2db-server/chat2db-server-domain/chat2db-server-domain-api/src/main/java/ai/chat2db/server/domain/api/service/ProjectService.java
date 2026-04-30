package ai.chat2db.server.domain.api.service;

import java.util.List;

import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.project.ProjectCreateParam;
import ai.chat2db.server.domain.api.param.project.ProjectUpdateParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

public interface ProjectService {

    DataResult<Long> create(ProjectCreateParam param);

    DataResult<Long> update(ProjectUpdateParam param);

    ActionResult delete(Long id);

    ListResult<Project> queryList();

    DataResult<Project> query(Long id);

    List<Project> queryList(List<Long> ids);
}
