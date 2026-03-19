package ai.chat2db.server.web.api.controller.data.source;

import java.util.List;

import ai.chat2db.server.domain.api.model.DataSourceGroup;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceGroupUpdateParam;
import ai.chat2db.server.domain.api.service.DataSourceGroupService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceGroupCreateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceGroupUpdateRequest;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceGroupVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConnectionInfoAspect
@RequestMapping("/api/connection/group")
@RestController
public class DataSourceGroupController {

    @Autowired
    private DataSourceGroupService dataSourceGroupService;

    @GetMapping("/list")
    public ListResult<DataSourceGroupVO> list() {
        List<DataSourceGroup> groups = dataSourceGroupService.queryCurrentUserList().getData();
        List<DataSourceGroupVO> result = groups.stream().map(this::toVO).toList();
        return ListResult.of(result);
    }

    @PostMapping("/create")
    public DataResult<Long> create(@RequestBody DataSourceGroupCreateRequest request) {
        DataSourceGroupCreateParam param = new DataSourceGroupCreateParam();
        param.setName(request.getName());
        return dataSourceGroupService.create(param);
    }

    @PostMapping("/update")
    public DataResult<Long> update(@RequestBody DataSourceGroupUpdateRequest request) {
        DataSourceGroupUpdateParam param = new DataSourceGroupUpdateParam();
        param.setId(request.getId());
        param.setName(request.getName());
        return dataSourceGroupService.update(param);
    }

    @DeleteMapping("/{id}")
    public ActionResult delete(@PathVariable("id") Long id) {
        return dataSourceGroupService.delete(id);
    }

    private DataSourceGroupVO toVO(DataSourceGroup group) {
        DataSourceGroupVO vo = new DataSourceGroupVO();
        vo.setId(group.getId());
        vo.setName(group.getName());
        return vo;
    }
}
