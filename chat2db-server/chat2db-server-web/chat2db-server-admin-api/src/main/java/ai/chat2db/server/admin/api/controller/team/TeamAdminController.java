
package ai.chat2db.server.admin.api.controller.team;

import ai.chat2db.server.common.api.audit.AdminAudit;
import ai.chat2db.server.admin.api.controller.team.converter.TeamAdminConverter;
import ai.chat2db.server.admin.api.controller.team.request.TeamCreateRequest;
import ai.chat2db.server.admin.api.controller.team.request.TeamUpdateRequest;
import ai.chat2db.server.admin.api.controller.team.vo.TeamPageQueryVO;
import ai.chat2db.server.common.api.controller.request.CommonPageQueryRequest;
import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditResourceTypeEnum;
import ai.chat2db.server.domain.api.param.team.TeamPageQueryParam;
import ai.chat2db.server.domain.api.param.team.TeamPageQueryParam.OrderCondition;
import ai.chat2db.server.domain.api.param.team.TeamSelector;
import ai.chat2db.server.domain.api.service.TeamService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Team Management
 *
 * @author Jiaju Zhuang
 */
@RequestMapping("/api/admin/team")
@RestController
public class TeamAdminController {
    private static final TeamSelector TEAM_SELECTOR = TeamSelector.builder()
        .modifiedUser(Boolean.TRUE)
        .build();

    @Resource
    private TeamService teamService;
    @Resource
    private TeamAdminConverter teamAdminConverter;

    /**
     * Pagination query
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @GetMapping("/page")
    public WebPageResult<TeamPageQueryVO> page(@Valid CommonPageQueryRequest request) {
        TeamPageQueryParam param = teamAdminConverter.request2param(request);
        param.orderBy(OrderCondition.ID_DESC);
        return teamService.pageQuery(param, TEAM_SELECTOR)
            .mapToWeb(teamAdminConverter::dto2vo);
    }

    /**
     * create
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @PostMapping("/create")
    @AdminAudit(actionType = AuditActionTypeEnum.CREATE, resourceType = AuditResourceTypeEnum.TEAM)
    public DataResult<Long> create(@RequestBody TeamCreateRequest request) {
        return teamService.create(teamAdminConverter.request2param(request));
    }

    /**
     * update
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @PostMapping("/update")
    @AdminAudit(actionType = AuditActionTypeEnum.UPDATE, resourceType = AuditResourceTypeEnum.TEAM)
    public DataResult<Long> update(@RequestBody TeamUpdateRequest request) {
        return teamService.update(teamAdminConverter.request2param(request));
    }

    /**
     * delete
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @AdminAudit(actionType = AuditActionTypeEnum.DELETE, resourceType = AuditResourceTypeEnum.TEAM)
    public DataResult<Boolean> delete(@PathVariable Long id) {
        return teamService.delete(id).toBooleaSuccessnDataResult();
    }
}
