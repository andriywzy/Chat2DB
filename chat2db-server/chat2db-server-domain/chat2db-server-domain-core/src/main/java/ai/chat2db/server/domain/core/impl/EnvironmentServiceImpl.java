package ai.chat2db.server.domain.core.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.param.EnvironmentPageQueryParam;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.domain.core.converter.EnvironmentConverter;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceDO;
import ai.chat2db.server.domain.repository.entity.EnvironmentDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceMapper;
import ai.chat2db.server.domain.repository.mapper.EnvironmentMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.util.ContextUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * environment
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class EnvironmentServiceImpl implements EnvironmentService {



    private EnvironmentMapper getMapper() {
        return Dbutils.getMapper(EnvironmentMapper.class);
    }

    private DataSourceMapper getDataSourceMapper() {
        return Dbutils.getMapper(DataSourceMapper.class);
    }

    @Resource
    private EnvironmentConverter environmentConverter;
    @Resource
    private ProjectService projectService;

    @Override
    public ListResult<Environment> listQuery(List<Long> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        LambdaQueryWrapper<EnvironmentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(EnvironmentDO::getId, idList);
        List<EnvironmentDO> dataList = getMapper().selectList(queryWrapper);
        List<Environment> list = environmentConverter.do2dto(dataList);
        return ListResult.of(list);
    }

    @Override
    public PageResult<Environment> pageQuery(EnvironmentPageQueryParam param) {
        LambdaQueryWrapper<EnvironmentDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(EnvironmentDO::getName, "%" + param.getSearchKey() + "%")
                .or()
                .like(EnvironmentDO::getShortName, "%" + param.getSearchKey() + "%"));
        }
        IPage<EnvironmentDO> iPage = getMapper().selectPage(new Page<>(param.getPageNo(), param.getPageSize()),
            queryWrapper);
        List<Environment> dataList = environmentConverter.do2dto(iPage.getRecords());
        return PageResult.of(dataList, iPage.getTotal(), param);
    }

    @Override
    public ListResult<Environment> queryList() {
        materializeMissingBoundEnvironments();
        LambdaQueryWrapper<EnvironmentDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(EnvironmentDO::getProjectId, EnvironmentDO::getName, EnvironmentDO::getId);
        return ListResult.of(environmentConverter.do2dto(getMapper().selectList(queryWrapper)));
    }

    @Override
    public DataResult<Environment> query(Long id) {
        if (id == null) {
            return DataResult.error("common.paramError", "Invalid environment id");
        }
        materializeMissingBoundEnvironments();
        EnvironmentDO data = getMapper().selectById(id);
        if (data == null) {
            return DataResult.error("common.dataNotFound", "Environment not found");
        }
        return DataResult.of(environmentConverter.do2dto(List.of(data)).stream().findFirst().orElse(null));
    }

    @Override
    public DataResult<Long> create(Environment environment) {
        if (environment == null || StringUtils.isBlank(environment.getName())) {
            return DataResult.error("common.paramError", "Environment name is required");
        }
        EnvironmentDO data = new EnvironmentDO();
        data.setName(environment.getName().trim());
        data.setShortName(StringUtils.defaultIfBlank(environment.getShortName(), environment.getName().trim()));
        data.setColor(StringUtils.defaultIfBlank(environment.getColor(), "BLUE"));
        data.setScopeType(StringUtils.defaultIfBlank(environment.getScopeType(), "USER"));
        data.setScopeId(environment.getScopeId() == null ? ContextUtils.getUserId() : environment.getScopeId());
        data.setProjectId(environment.getProjectId());
        data.setCreateUserId(ContextUtils.getUserId());
        data.setModifiedUserId(ContextUtils.getUserId());
        getMapper().insert(data);
        return DataResult.of(data.getId());
    }

    @Override
    public DataResult<Long> update(Environment environment) {
        if (environment == null || environment.getId() == null || StringUtils.isBlank(environment.getName())) {
            return DataResult.error("common.paramError", "Environment id and name are required");
        }
        materializeMissingBoundEnvironments();
        EnvironmentDO existing = getMapper().selectById(environment.getId());
        if (existing == null) {
            return DataResult.error("common.dataNotFound", "Environment not found");
        }
        if (!canManage(existing)) {
            return DataResult.error("common.forbidden", "No permission to manage this environment");
        }
        existing.setName(environment.getName().trim());
        existing.setShortName(StringUtils.defaultIfBlank(environment.getShortName(), environment.getName().trim()));
        existing.setColor(StringUtils.defaultIfBlank(environment.getColor(), existing.getColor()));
        existing.setScopeType(StringUtils.defaultIfBlank(environment.getScopeType(), existing.getScopeType()));
        existing.setScopeId(environment.getScopeId() == null ? existing.getScopeId() : environment.getScopeId());
        existing.setProjectId(environment.getProjectId());
        existing.setModifiedUserId(ContextUtils.getUserId());
        getMapper().updateById(existing);
        return DataResult.of(existing.getId());
    }

    @Override
    public ActionResult delete(Long id) {
        if (id == null) {
            return ActionResult.fail("common.paramError", "Invalid environment id", null);
        }
        materializeMissingBoundEnvironments();
        EnvironmentDO existing = getMapper().selectById(id);
        if (existing == null) {
            return ActionResult.fail("common.dataNotFound", "Environment not found", null);
        }
        if (!canManage(existing)) {
            return ActionResult.fail("common.forbidden", "No permission to manage this environment", null);
        }
        getMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    private boolean canManage(EnvironmentDO environmentDO) {
        Long projectOwnerId = null;
        if (environmentDO.getProjectId() != null) {
            var projectResult = projectService.query(environmentDO.getProjectId());
            if (projectResult.success() && projectResult.getData() != null) {
                projectOwnerId = projectResult.getData().getUserId();
            }
        }
        return Boolean.TRUE.equals(ContextUtils.getLoginUser().getAdmin())
            || Objects.equals(ContextUtils.getUserId(), environmentDO.getCreateUserId())
            || Objects.equals(ContextUtils.getUserId(), projectOwnerId);
    }

    private void materializeMissingBoundEnvironments() {
        LambdaQueryWrapper<DataSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.isNotNull(DataSourceDO::getEnvironmentId)
            .orderByAsc(DataSourceDO::getId);
        List<DataSourceDO> dataSourceList = getDataSourceMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(dataSourceList)) {
            return;
        }

        Map<Long, DataSourceDO> dataSourceByEnvironmentId = new LinkedHashMap<>();
        for (DataSourceDO dataSourceDO : dataSourceList) {
            dataSourceByEnvironmentId.putIfAbsent(dataSourceDO.getEnvironmentId(), dataSourceDO);
        }
        Set<Long> environmentIds = dataSourceByEnvironmentId.keySet();
        Set<Long> existingEnvironmentIds = getMapper().selectBatchIds(environmentIds).stream()
            .map(EnvironmentDO::getId)
            .collect(Collectors.toSet());

        Date now = new Date();
        for (Map.Entry<Long, DataSourceDO> entry : dataSourceByEnvironmentId.entrySet()) {
            Long environmentId = entry.getKey();
            if (existingEnvironmentIds.contains(environmentId)) {
                continue;
            }
            DataSourceDO dataSourceDO = entry.getValue();
            EnvironmentDO environmentDO = new EnvironmentDO();
            environmentDO.setId(environmentId);
            environmentDO.setName("Environment " + environmentId);
            environmentDO.setShortName("Environment " + environmentId);
            environmentDO.setColor("BLUE");
            environmentDO.setScopeType("USER");
            environmentDO.setScopeId(dataSourceDO.getUserId());
            environmentDO.setProjectId(dataSourceDO.getProjectId());
            environmentDO.setCreateUserId(dataSourceDO.getUserId());
            environmentDO.setModifiedUserId(dataSourceDO.getUserId());
            environmentDO.setGmtCreate(now);
            environmentDO.setGmtModified(now);
            getMapper().insert(environmentDO);
            existingEnvironmentIds.add(environmentId);
        }
    }
}
