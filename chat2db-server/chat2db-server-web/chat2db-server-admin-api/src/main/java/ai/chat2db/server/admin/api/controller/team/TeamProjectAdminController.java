package ai.chat2db.server.admin.api.controller.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.chat2db.server.admin.api.controller.project.vo.SimpleProjectVO;
import ai.chat2db.server.common.api.audit.AdminAudit;
import ai.chat2db.server.admin.api.controller.team.request.TeamPageCommonQueryRequest;
import ai.chat2db.server.admin.api.controller.team.request.TeamProjectBatchCreateRequest;
import ai.chat2db.server.admin.api.controller.team.request.TeamProjectGrantRequest;
import ai.chat2db.server.admin.api.controller.team.vo.TeamProjectPageQueryVO;
import ai.chat2db.server.common.api.controller.vo.SimpleEnvironmentVO;
import ai.chat2db.server.domain.api.enums.AccessObjectTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditResourceTypeEnum;
import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.ProjectAccessEnvironmentDO;
import ai.chat2db.server.domain.repository.entity.ProjectAccessDO;
import ai.chat2db.server.domain.repository.mapper.ProjectAccessEnvironmentMapper;
import ai.chat2db.server.domain.repository.mapper.ProjectAccessMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin/team/project")
@RestController
public class TeamProjectAdminController {

    @Resource
    private ProjectService projectService;
    @Resource
    private EnvironmentService environmentService;

    private ProjectAccessMapper getMapper() {
        return Dbutils.getMapper(ProjectAccessMapper.class);
    }

    private ProjectAccessEnvironmentMapper getEnvironmentMapper() {
        return Dbutils.getMapper(ProjectAccessEnvironmentMapper.class);
    }

    @GetMapping("/page")
    public WebPageResult<TeamProjectPageQueryVO> page(@Valid TeamPageCommonQueryRequest request) {
        LambdaQueryWrapper<ProjectAccessDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectAccessDO::getAccessObjectType, AccessObjectTypeEnum.TEAM.getCode())
            .eq(ProjectAccessDO::getAccessObjectId, request.getTeamId())
            .orderByDesc(ProjectAccessDO::getId);
        List<ProjectAccessDO> accessList = getMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(accessList)) {
            return WebPageResult.of(List.of(), 0L, request.getPageNo(), request.getPageSize());
        }

        List<Long> projectIds = EasyCollectionUtils.toList(accessList, ProjectAccessDO::getProjectId);
        Map<Long, Project> projectMap = EasyCollectionUtils.toIdentityMap(projectService.queryList(projectIds), Project::getId);
        Map<Long, List<SimpleEnvironmentVO>> environmentMap = queryEnvironmentMap(accessList);
        String searchKey = StringUtils.trimToEmpty(request.getSearchKey()).toLowerCase();

        List<TeamProjectPageQueryVO> rows = new ArrayList<>();
        for (ProjectAccessDO access : accessList) {
            Project project = projectMap.get(access.getProjectId());
            if (project == null) {
                continue;
            }
            if (StringUtils.isNotBlank(searchKey) && !StringUtils.containsIgnoreCase(project.getName(), searchKey)) {
                continue;
            }
            TeamProjectPageQueryVO vo = new TeamProjectPageQueryVO();
            vo.setId(access.getId());
            vo.setTeamId(access.getAccessObjectId());
            SimpleProjectVO projectVO = new SimpleProjectVO();
            projectVO.setId(project.getId());
            projectVO.setName(project.getName());
            projectVO.setDescription(project.getDescription());
            vo.setProject(projectVO);
            vo.setEnvironmentList(environmentMap.getOrDefault(access.getId(), List.of()));
            rows.add(vo);
        }

        int fromIndex = Math.max((request.getPageNo() - 1) * request.getPageSize(), 0);
        int toIndex = Math.min(fromIndex + request.getPageSize(), rows.size());
        List<TeamProjectPageQueryVO> pageData = fromIndex >= rows.size() ? List.of() : rows.subList(fromIndex, toIndex);
        return WebPageResult.of(pageData, (long) rows.size(), request.getPageNo(), request.getPageSize());
    }

    @PostMapping("/batch_create")
    @AdminAudit(actionType = AuditActionTypeEnum.GRANT, resourceType = AuditResourceTypeEnum.TEAM_PROJECT)
    public ActionResult create(@Valid @RequestBody TeamProjectBatchCreateRequest request) {
        for (TeamProjectGrantRequest grant : normalizeRequest(request)) {
            validateGrant(grant);
            ProjectAccessDO access = queryExistingAccess(request.getTeamId(), grant.getProjectId());
            if (access == null) {
                access = new ProjectAccessDO();
                access.setProjectId(grant.getProjectId());
                access.setAccessObjectType(AccessObjectTypeEnum.TEAM.getCode());
                access.setAccessObjectId(request.getTeamId());
                access.setPermissionType("VIEW");
                access.setGmtCreate(DateUtil.date());
                access.setGmtModified(DateUtil.date());
                getMapper().insert(access);
            } else {
                access.setGmtModified(DateUtil.date());
                getMapper().updateById(access);
            }
            replaceEnvironmentScopes(access.getId(), grant.getEnvironmentIdList());
        }
        return ActionResult.isSuccess();
    }

    @DeleteMapping("/{id}")
    @AdminAudit(actionType = AuditActionTypeEnum.DELETE, resourceType = AuditResourceTypeEnum.TEAM_PROJECT)
    public DataResult<Boolean> delete(@PathVariable Long id) {
        LambdaQueryWrapper<ProjectAccessEnvironmentDO> environmentQueryWrapper = new LambdaQueryWrapper<>();
        environmentQueryWrapper.eq(ProjectAccessEnvironmentDO::getProjectAccessId, id);
        getEnvironmentMapper().delete(environmentQueryWrapper);
        return DataResult.of(getMapper().deleteById(id) > 0);
    }

    private List<TeamProjectGrantRequest> normalizeRequest(TeamProjectBatchCreateRequest request) {
        List<TeamProjectGrantRequest> grantList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(request.getProjectGrantList())) {
            grantList.addAll(request.getProjectGrantList());
        }
        if (CollectionUtils.isNotEmpty(request.getProjectIdList())) {
            for (Long projectId : request.getProjectIdList()) {
                if (projectId == null) {
                    continue;
                }
                TeamProjectGrantRequest grant = new TeamProjectGrantRequest();
                grant.setProjectId(projectId);
                grant.setEnvironmentIdList(List.of());
                grantList.add(grant);
            }
        }
        return grantList.stream()
            .filter(grant -> grant != null && grant.getProjectId() != null)
            .collect(Collectors.toMap(
                TeamProjectGrantRequest::getProjectId,
                grant -> grant,
                (left, right) -> right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
    }

    private ProjectAccessDO queryExistingAccess(Long teamId, Long projectId) {
        LambdaQueryWrapper<ProjectAccessDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectAccessDO::getProjectId, projectId)
            .eq(ProjectAccessDO::getAccessObjectType, AccessObjectTypeEnum.TEAM.getCode())
            .eq(ProjectAccessDO::getAccessObjectId, teamId)
            .last("limit 1");
        return getMapper().selectOne(queryWrapper);
    }

    private void replaceEnvironmentScopes(Long projectAccessId, List<Long> environmentIdList) {
        LambdaQueryWrapper<ProjectAccessEnvironmentDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(ProjectAccessEnvironmentDO::getProjectAccessId, projectAccessId);
        getEnvironmentMapper().delete(deleteWrapper);

        if (CollectionUtils.isEmpty(environmentIdList)) {
            return;
        }

        Date now = DateUtil.date();
        for (Long environmentId : environmentIdList.stream().filter(Objects::nonNull).distinct().toList()) {
            ProjectAccessEnvironmentDO mapping = new ProjectAccessEnvironmentDO();
            mapping.setProjectAccessId(projectAccessId);
            mapping.setEnvironmentId(environmentId);
            mapping.setGmtCreate(now);
            mapping.setGmtModified(now);
            getEnvironmentMapper().insert(mapping);
        }
    }

    private void validateGrant(TeamProjectGrantRequest grant) {
        Project project = projectService.query(grant.getProjectId()).getData();
        if (project == null) {
            throw new ParamBusinessException("projectId invalid");
        }
        if (CollectionUtils.isEmpty(grant.getEnvironmentIdList())) {
            return;
        }
        for (Long environmentId : grant.getEnvironmentIdList().stream().filter(Objects::nonNull).distinct().toList()) {
            Environment environment = environmentService.query(environmentId).getData();
            if (environment == null || !Objects.equals(environment.getProjectId(), project.getId())) {
                throw new ParamBusinessException("environmentId invalid");
            }
        }
    }

    private Map<Long, List<SimpleEnvironmentVO>> queryEnvironmentMap(List<ProjectAccessDO> accessList) {
        if (CollectionUtils.isEmpty(accessList)) {
            return Collections.emptyMap();
        }
        List<Long> accessIds = EasyCollectionUtils.toList(accessList, ProjectAccessDO::getId);
        List<ProjectAccessEnvironmentDO> mappings = getEnvironmentMapper().selectList(new LambdaQueryWrapper<>());
        if (CollectionUtils.isEmpty(mappings)) {
            return Collections.emptyMap();
        }
        Map<Long, Environment> allEnvironmentMap = environmentService.queryList()
            .getData()
            .stream()
            .collect(Collectors.toMap(Environment::getId, environment -> environment, (left, right) -> left));
        Map<Long, List<SimpleEnvironmentVO>> result = new LinkedHashMap<>();
        for (ProjectAccessEnvironmentDO mapping : mappings.stream()
            .filter(mapping -> accessIds.contains(mapping.getProjectAccessId()))
            .sorted(java.util.Comparator.comparing(ProjectAccessEnvironmentDO::getId))
            .toList()) {
            Environment environment = allEnvironmentMap.get(mapping.getEnvironmentId());
            if (environment == null) {
                continue;
            }
            SimpleEnvironmentVO vo = new SimpleEnvironmentVO();
            vo.setId(environment.getId());
            vo.setName(environment.getName());
            vo.setShortName(environment.getShortName());
            vo.setColor(environment.getColor());
            result.computeIfAbsent(mapping.getProjectAccessId(), key -> new ArrayList<>()).add(vo);
        }
        return result;
    }
}
