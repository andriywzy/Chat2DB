package ai.chat2db.server.domain.api.service;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSourceGroup;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupUpdateParam;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;

public interface DataSourceGroupService {

    DataResult<Long> create(DataSourceGroupCreateParam param);

    DataResult<Long> update(DataSourceGroupUpdateParam param);

    ActionResult delete(Long id);

    ListResult<DataSourceGroup> queryCurrentUserList();

    DataResult<DataSourceGroup> queryCurrentUserGroup(Long id);

    List<DataSourceGroup> queryCurrentUserList(List<Long> ids);
}
