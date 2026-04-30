package ai.chat2db.server.web.start.service.sso;

import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.SsoGroupTeamMappingDO;
import ai.chat2db.server.domain.repository.entity.TeamUserBindingSourceDO;
import ai.chat2db.server.domain.repository.entity.TeamUserDO;
import ai.chat2db.server.domain.repository.mapper.TeamUserBindingSourceMapper;
import ai.chat2db.server.domain.repository.mapper.TeamUserMapper;
import ai.chat2db.server.web.start.config.sso.SsoConstants;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
public class SsoTeamSyncService {

    private final SsoGroupMappingService groupMappingService;

    public SsoTeamSyncService(SsoGroupMappingService groupMappingService) {
        this.groupMappingService = groupMappingService;
    }

    private TeamUserMapper getTeamUserMapper() {
        return Dbutils.getMapper(TeamUserMapper.class);
    }

    private TeamUserBindingSourceMapper getTeamUserBindingSourceMapper() {
        return Dbutils.getMapper(TeamUserBindingSourceMapper.class);
    }

    public void syncUserTeamMembership(Long userId, String issuer, List<String> groups) {
        cleanupOrphanBindingSources();

        Map<Long, List<SsoGroupTeamMappingDO>> desiredMappings = groupMappingService.listByIssuerAndGroups(issuer, groups);
        List<TeamUserDO> currentMemberships = getTeamUserMapper().selectList(
            new LambdaQueryWrapper<TeamUserDO>().eq(TeamUserDO::getUserId, userId)
        );
        Map<Long, TeamUserDO> teamUserByTeamId = new LinkedHashMap<>();
        for (TeamUserDO membership : currentMemberships) {
            teamUserByTeamId.put(membership.getTeamId(), membership);
        }

        for (Map.Entry<Long, List<SsoGroupTeamMappingDO>> entry : desiredMappings.entrySet()) {
            Long teamId = entry.getKey();
            TeamUserDO teamUser = teamUserByTeamId.get(teamId);
            if (teamUser == null) {
                teamUser = createSsoMembership(userId, teamId);
                teamUserByTeamId.put(teamId, teamUser);
            }
            for (SsoGroupTeamMappingDO mapping : entry.getValue()) {
                ensureBindingSource(teamUser.getId(), issuer, mapping.getExternalGroupCode());
            }
        }

        List<TeamUserBindingSourceDO> existingSources = queryUserBindingSources(userId, issuer);
        Set<String> desiredGroupKeys = desiredMappings.values().stream()
            .flatMap(List::stream)
            .map(mapping -> mapping.getTeamId() + "::" + mapping.getExternalGroupCode())
            .collect(Collectors.toSet());
        for (TeamUserBindingSourceDO source : existingSources) {
            TeamUserDO teamUser = teamUserByTeamId.values().stream()
                .filter(item -> Objects.equals(item.getId(), source.getTeamUserId()))
                .findFirst()
                .orElseGet(() -> getTeamUserMapper().selectById(source.getTeamUserId()));
            if (teamUser == null) {
                getTeamUserBindingSourceMapper().deleteById(source.getId());
                continue;
            }
            String key = teamUser.getTeamId() + "::" + source.getExternalGroupCode();
            if (desiredGroupKeys.contains(key)) {
                continue;
            }
            getTeamUserBindingSourceMapper().deleteById(source.getId());
            cleanupEmptySsoMembership(teamUser.getId());
        }
    }

    private TeamUserDO createSsoMembership(Long userId, Long teamId) {
        TeamUserDO teamUser = new TeamUserDO();
        teamUser.setUserId(userId);
        teamUser.setTeamId(teamId);
        teamUser.setSourceType(SsoConstants.TEAM_USER_SOURCE_SSO);
        teamUser.setCreateUserId(2L);
        teamUser.setModifiedUserId(2L);
        LocalDateTime now = DateUtil.date().toLocalDateTime();
        teamUser.setGmtCreate(now);
        teamUser.setGmtModified(now);
        getTeamUserMapper().insert(teamUser);
        return teamUser;
    }

    private void ensureBindingSource(Long teamUserId, String issuer, String externalGroupCode) {
        LambdaQueryWrapper<TeamUserBindingSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamUserBindingSourceDO::getTeamUserId, teamUserId)
            .eq(TeamUserBindingSourceDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
            .eq(TeamUserBindingSourceDO::getIssuer, issuer)
            .eq(TeamUserBindingSourceDO::getExternalGroupCode, externalGroupCode)
            .last("limit 1");
        TeamUserBindingSourceDO existing = getTeamUserBindingSourceMapper().selectOne(queryWrapper);
        if (existing != null) {
            existing.setGmtModified(DateUtil.date().toLocalDateTime());
            getTeamUserBindingSourceMapper().updateById(existing);
            return;
        }
        TeamUserBindingSourceDO source = new TeamUserBindingSourceDO();
        source.setTeamUserId(teamUserId);
        source.setProviderType(SsoConstants.PROVIDER_TYPE_OIDC);
        source.setIssuer(issuer);
        source.setExternalGroupCode(externalGroupCode);
        source.setSyncSource(SsoConstants.TEAM_USER_SYNC_SOURCE);
        source.setGmtCreate(DateUtil.date().toLocalDateTime());
        source.setGmtModified(DateUtil.date().toLocalDateTime());
        getTeamUserBindingSourceMapper().insert(source);
    }

    private List<TeamUserBindingSourceDO> queryUserBindingSources(Long userId, String issuer) {
        List<TeamUserDO> teamUsers = getTeamUserMapper().selectList(new LambdaQueryWrapper<TeamUserDO>().eq(TeamUserDO::getUserId, userId));
        if (CollectionUtils.isEmpty(teamUsers)) {
            return List.of();
        }
        List<Long> teamUserIds = teamUsers.stream().map(TeamUserDO::getId).toList();
        LambdaQueryWrapper<TeamUserBindingSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(TeamUserBindingSourceDO::getTeamUserId, teamUserIds)
            .eq(TeamUserBindingSourceDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
            .eq(TeamUserBindingSourceDO::getIssuer, issuer);
        return getTeamUserBindingSourceMapper().selectList(queryWrapper);
    }

    private void cleanupEmptySsoMembership(Long teamUserId) {
        TeamUserDO teamUser = getTeamUserMapper().selectById(teamUserId);
        if (teamUser == null || !SsoConstants.TEAM_USER_SOURCE_SSO.equals(teamUser.getSourceType())) {
            return;
        }
        LambdaQueryWrapper<TeamUserBindingSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TeamUserBindingSourceDO::getTeamUserId, teamUserId);
        Integer count = Math.toIntExact(getTeamUserBindingSourceMapper().selectCount(queryWrapper));
        if (count == 0) {
            getTeamUserMapper().deleteById(teamUserId);
        }
    }

    private void cleanupOrphanBindingSources() {
        List<TeamUserBindingSourceDO> sourceList = getTeamUserBindingSourceMapper().selectList(new LambdaQueryWrapper<>());
        if (CollectionUtils.isEmpty(sourceList)) {
            return;
        }
        Set<Long> teamUserIds = sourceList.stream().map(TeamUserBindingSourceDO::getTeamUserId).collect(Collectors.toSet());
        List<TeamUserDO> teamUsers = getTeamUserMapper().selectBatchIds(new ArrayList<>(teamUserIds));
        Set<Long> existingIds = teamUsers.stream().map(TeamUserDO::getId).collect(Collectors.toSet());
        for (TeamUserBindingSourceDO source : sourceList) {
            if (!existingIds.contains(source.getTeamUserId())) {
                getTeamUserBindingSourceMapper().deleteById(source.getId());
            }
        }
    }
}
