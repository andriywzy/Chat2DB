package ai.chat2db.server.web.api.controller.project;

import java.util.List;
import java.util.Set;

import ai.chat2db.server.common.api.audit.AdminAudit;
import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditResourceTypeEnum;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.project.ProjectCreateParam;
import ai.chat2db.server.domain.api.param.project.ProjectUpdateParam;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.core.util.ProjectPermissionUtils;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.project.request.ProjectCreateRequest;
import ai.chat2db.server.web.api.controller.project.request.ProjectUpdateRequest;
import ai.chat2db.server.web.api.controller.project.vo.ProjectVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ConnectionInfoAspect
@RequestMapping("/api/project")
@RestController
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @GetMapping("/list")
    public ListResult<ProjectVO> list() {
        Set<Long> teamAdminProjectIds = ProjectPermissionUtils.getTeamAdminProjectIds();
        List<Project> projects = projectService.queryList().getData();
        List<ProjectVO> result = projects.stream()
            .map(project -> toVO(project, teamAdminProjectIds))
            .toList();
        return ListResult.of(result);
    }

    @PostMapping("/create")
    @AdminAudit(actionType = AuditActionTypeEnum.CREATE, resourceType = AuditResourceTypeEnum.PROJECT)
    public DataResult<Long> create(@RequestBody ProjectCreateRequest request) {
        ProjectCreateParam param = new ProjectCreateParam();
        param.setName(request.getName());
        param.setDescription(request.getDescription());
        param.setScopeType(request.getScopeType());
        param.setScopeId(request.getScopeId());
        return projectService.create(param);
    }

    @PostMapping("/update")
    @AdminAudit(actionType = AuditActionTypeEnum.UPDATE, resourceType = AuditResourceTypeEnum.PROJECT)
    public DataResult<Long> update(@RequestBody ProjectUpdateRequest request) {
        ProjectUpdateParam param = new ProjectUpdateParam();
        param.setId(request.getId());
        param.setName(request.getName());
        param.setDescription(request.getDescription());
        return projectService.update(param);
    }

    @DeleteMapping("/{id}")
    @AdminAudit(actionType = AuditActionTypeEnum.DELETE, resourceType = AuditResourceTypeEnum.PROJECT)
    public ActionResult delete(@PathVariable("id") Long id) {
        return projectService.delete(id);
    }

    private ProjectVO toVO(Project project, Set<Long> teamAdminProjectIds) {
        ProjectVO vo = new ProjectVO();
        vo.setId(project.getId());
        vo.setName(project.getName());
        vo.setDescription(project.getDescription());
        vo.setScopeType(project.getScopeType());
        vo.setScopeId(project.getScopeId());
        vo.setCanManage(ContextUtils.getLoginUser().getAdmin()
            || ContextUtils.getUserId().equals(project.getUserId())
            || teamAdminProjectIds.contains(project.getId()));
        return vo;
    }
}
