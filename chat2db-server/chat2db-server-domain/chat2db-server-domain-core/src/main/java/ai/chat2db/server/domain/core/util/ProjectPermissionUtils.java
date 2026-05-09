package ai.chat2db.server.domain.core.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.chat2db.server.domain.api.enums.AccessObjectTypeEnum;
import ai.chat2db.server.domain.api.enums.ProjectPermissionTypeEnum;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.ProjectAccessDO;
import ai.chat2db.server.domain.repository.entity.TeamUserDO;
import ai.chat2db.server.domain.repository.mapper.ProjectAccessMapper;
import ai.chat2db.server.domain.repository.mapper.TeamUserMapper;
import ai.chat2db.server.tools.common.util.ContextUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.commons.collections4.CollectionUtils;

public class ProjectPermissionUtils {

    private static ProjectAccessMapper getProjectAccessMapper() {
        return Dbutils.getMapper(ProjectAccessMapper.class);
    }

    private static TeamUserMapper getTeamUserMapper() {
        return Dbutils.getMapper(TeamUserMapper.class);
    }

    public static boolean hasProjectTeamAdminPermission(Long projectId) {
        if (projectId == null) {
            return false;
        }
        if (PermissionUtils.hasDeskTopOrAdminPermission()) {
            return true;
        }
        return getTeamAdminProjectIds().contains(projectId);
    }

    public static Set<Long> getTeamAdminProjectIds() {
        Long userId = ContextUtils.getUserId();
        if (userId == null) {
            return Collections.emptySet();
        }

        Set<Long> teamIds = getCurrentTeamIds(userId);
        LambdaQueryWrapper<ProjectAccessDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProjectAccessDO::getPermissionType, ProjectPermissionTypeEnum.TEAM_ADMIN.getCode())
            .and(wrapper -> wrapper.eq(ProjectAccessDO::getAccessObjectType, AccessObjectTypeEnum.USER.getCode())
                .eq(ProjectAccessDO::getAccessObjectId, userId)
                .or(inner -> {
                    if (teamIds.isEmpty()) {
                        inner.apply("1 = 0");
                    } else {
                        inner.eq(ProjectAccessDO::getAccessObjectType, AccessObjectTypeEnum.TEAM.getCode())
                            .in(ProjectAccessDO::getAccessObjectId, teamIds);
                    }
                }));

        List<ProjectAccessDO> accessList = getProjectAccessMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(accessList)) {
            return Collections.emptySet();
        }
        Set<Long> projectIds = new HashSet<>();
        accessList.stream()
            .map(ProjectAccessDO::getProjectId)
            .forEach(projectIds::add);
        return projectIds;
    }

    private static Set<Long> getCurrentTeamIds(Long userId) {
        LambdaQueryWrapper<TeamUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamUserDO::getUserId, userId);
        List<TeamUserDO> teamUserList = getTeamUserMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(teamUserList)) {
            return Collections.emptySet();
        }
        Set<Long> teamIds = new HashSet<>();
        teamUserList.stream()
            .map(TeamUserDO::getTeamId)
            .forEach(teamIds::add);
        return teamIds;
    }
}
