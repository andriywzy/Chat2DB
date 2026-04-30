package ai.chat2db.server.web.start.service.sso;

import ai.chat2db.server.admin.api.controller.team.vo.SimpleTeamVO;
import ai.chat2db.server.domain.api.model.Team;
import ai.chat2db.server.domain.api.service.TeamService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.SsoGroupTeamMappingDO;
import ai.chat2db.server.domain.repository.mapper.SsoGroupTeamMappingMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.web.start.config.sso.SsoConstants;
import ai.chat2db.server.web.start.controller.sso.request.SsoGroupMappingRequest;
import ai.chat2db.server.web.start.controller.sso.vo.SsoGroupMappingVO;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SsoGroupMappingService {

    private final TeamService teamService;

    public SsoGroupMappingService(TeamService teamService) {
        this.teamService = teamService;
    }

    private SsoGroupTeamMappingMapper getMapper() {
        return Dbutils.getMapper(SsoGroupTeamMappingMapper.class);
    }

    public WebPageResult<SsoGroupMappingVO> page(int pageNo, int pageSize, String searchKey) {
        LambdaQueryWrapper<SsoGroupTeamMappingDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(SsoGroupTeamMappingDO::getId);
        List<SsoGroupTeamMappingDO> mappingList = getMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(mappingList)) {
            return WebPageResult.of(List.of(), 0L, pageNo, pageSize);
        }

        Map<Long, Team> teamMap = EasyCollectionUtils.toIdentityMap(
            teamService.listQuery(EasyCollectionUtils.toList(mappingList, SsoGroupTeamMappingDO::getTeamId)).getData(),
            Team::getId
        );

        String normalizedSearchKey = StringUtils.trimToEmpty(searchKey).toLowerCase();
        List<SsoGroupMappingVO> rows = new ArrayList<>();
        for (SsoGroupTeamMappingDO mapping : mappingList) {
            Team team = teamMap.get(mapping.getTeamId());
            if (team == null) {
                continue;
            }
            if (StringUtils.isNotBlank(normalizedSearchKey)
                && !StringUtils.containsIgnoreCase(mapping.getExternalGroupCode(), normalizedSearchKey)
                && !StringUtils.containsIgnoreCase(team.getCode(), normalizedSearchKey)
                && !StringUtils.containsIgnoreCase(team.getName(), normalizedSearchKey)) {
                continue;
            }

            SsoGroupMappingVO vo = new SsoGroupMappingVO();
            vo.setId(mapping.getId());
            vo.setProviderType(mapping.getProviderType());
            vo.setIssuer(mapping.getIssuer());
            vo.setExternalGroupCode(mapping.getExternalGroupCode());
            vo.setSyncMode(mapping.getSyncMode());
            vo.setGmtModified(mapping.getGmtModified());

            SimpleTeamVO teamVO = new SimpleTeamVO();
            teamVO.setId(team.getId());
            teamVO.setCode(team.getCode());
            teamVO.setName(team.getName());
            vo.setTeam(teamVO);
            rows.add(vo);
        }

        int fromIndex = Math.max((pageNo - 1) * pageSize, 0);
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        List<SsoGroupMappingVO> pageData = fromIndex >= rows.size() ? List.of() : rows.subList(fromIndex, toIndex);
        return WebPageResult.of(pageData, (long) rows.size(), pageNo, pageSize);
    }

    public ActionResult create(SsoGroupMappingRequest request) {
        SsoGroupTeamMappingDO existing = queryExisting(request.getIssuer(), request.getExternalGroupCode(), request.getTeamId());
        if (existing != null) {
            return ActionResult.isSuccess();
        }
        SsoGroupTeamMappingDO mapping = new SsoGroupTeamMappingDO();
        mapping.setProviderType(SsoConstants.PROVIDER_TYPE_OIDC);
        mapping.setIssuer(StringUtils.trimToEmpty(request.getIssuer()));
        mapping.setExternalGroupCode(StringUtils.trimToEmpty(request.getExternalGroupCode()));
        mapping.setTeamId(request.getTeamId());
        mapping.setSyncMode(SsoConstants.SYNC_MODE_MEMBERSHIP);
        mapping.setGmtCreate(DateUtil.date().toLocalDateTime());
        mapping.setGmtModified(DateUtil.date().toLocalDateTime());
        getMapper().insert(mapping);
        return ActionResult.isSuccess();
    }

    public ActionResult update(SsoGroupMappingRequest request) {
        SsoGroupTeamMappingDO mapping = getMapper().selectById(request.getId());
        if (mapping == null) {
            return ActionResult.fail("common.dataNotFound", "Data not found", null);
        }
        SsoGroupTeamMappingDO duplicate = queryExisting(request.getIssuer(), request.getExternalGroupCode(), request.getTeamId());
        if (duplicate != null && !duplicate.getId().equals(mapping.getId())) {
            return ActionResult.fail("common.dataAlreadyExists", "Mapping already exists", null);
        }
        mapping.setIssuer(StringUtils.trimToEmpty(request.getIssuer()));
        mapping.setExternalGroupCode(StringUtils.trimToEmpty(request.getExternalGroupCode()));
        mapping.setTeamId(request.getTeamId());
        mapping.setGmtModified(DateUtil.date().toLocalDateTime());
        getMapper().updateById(mapping);
        return ActionResult.isSuccess();
    }

    public ActionResult delete(Long id) {
        getMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    public Map<Long, List<SsoGroupTeamMappingDO>> listByIssuerAndGroups(String issuer, List<String> groups) {
        if (CollectionUtils.isEmpty(groups)) {
            return Map.of();
        }
        LambdaQueryWrapper<SsoGroupTeamMappingDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SsoGroupTeamMappingDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
            .eq(SsoGroupTeamMappingDO::getIssuer, issuer)
            .in(SsoGroupTeamMappingDO::getExternalGroupCode, groups);
        List<SsoGroupTeamMappingDO> mappingList = getMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(mappingList)) {
            return Map.of();
        }
        Map<Long, List<SsoGroupTeamMappingDO>> result = new LinkedHashMap<>();
        for (SsoGroupTeamMappingDO mapping : mappingList) {
            result.computeIfAbsent(mapping.getTeamId(), key -> new ArrayList<>()).add(mapping);
        }
        return result;
    }

    private SsoGroupTeamMappingDO queryExisting(String issuer, String externalGroupCode, Long teamId) {
        LambdaQueryWrapper<SsoGroupTeamMappingDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SsoGroupTeamMappingDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
            .eq(SsoGroupTeamMappingDO::getIssuer, StringUtils.trimToEmpty(issuer))
            .eq(SsoGroupTeamMappingDO::getExternalGroupCode, StringUtils.trimToEmpty(externalGroupCode))
            .eq(SsoGroupTeamMappingDO::getTeamId, teamId)
            .last("limit 1");
        return getMapper().selectOne(queryWrapper);
    }
}
