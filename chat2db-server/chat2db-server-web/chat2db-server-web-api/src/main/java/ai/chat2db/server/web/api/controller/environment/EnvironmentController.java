package ai.chat2db.server.web.api.controller.environment;

import java.util.List;

import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.controller.environment.request.EnvironmentCreateRequest;
import ai.chat2db.server.web.api.controller.environment.request.EnvironmentUpdateRequest;
import ai.chat2db.server.web.api.controller.environment.vo.EnvironmentVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/environment")
@RestController
public class EnvironmentController {

    @Autowired
    private EnvironmentService environmentService;

    @GetMapping("/list")
    public ListResult<EnvironmentVO> list() {
        List<EnvironmentVO> result = environmentService.queryList()
            .getData()
            .stream()
            .map(this::toVO)
            .toList();
        return ListResult.of(result);
    }

    @PostMapping("/create")
    public DataResult<Long> create(@RequestBody EnvironmentCreateRequest request) {
        Environment environment = new Environment();
        environment.setName(request.getName());
        environment.setShortName(request.getShortName());
        environment.setColor(request.getColor());
        environment.setScopeType(request.getScopeType());
        environment.setScopeId(request.getScopeId());
        environment.setProjectId(request.getProjectId());
        return environmentService.create(environment);
    }

    @PostMapping("/update")
    public DataResult<Long> update(@RequestBody EnvironmentUpdateRequest request) {
        Environment environment = new Environment();
        environment.setId(request.getId());
        environment.setName(request.getName());
        environment.setShortName(request.getShortName());
        environment.setColor(request.getColor());
        environment.setScopeType(request.getScopeType());
        environment.setScopeId(request.getScopeId());
        environment.setProjectId(request.getProjectId());
        return environmentService.update(environment);
    }

    @DeleteMapping("/{id}")
    public ActionResult delete(@PathVariable("id") Long id) {
        return environmentService.delete(id);
    }

    private EnvironmentVO toVO(Environment environment) {
        EnvironmentVO vo = new EnvironmentVO();
        vo.setId(environment.getId());
        vo.setName(environment.getName());
        vo.setShortName(environment.getShortName());
        vo.setColor(environment.getColor());
        vo.setScopeType(environment.getScopeType());
        vo.setScopeId(environment.getScopeId());
        vo.setProjectId(environment.getProjectId());
        vo.setCanManage(Boolean.TRUE.equals(ContextUtils.getLoginUser().getAdmin())
            || ContextUtils.getUserId().equals(environment.getCreateUserId()));
        return vo;
    }
}
