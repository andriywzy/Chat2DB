package ai.chat2db.server.domain.core.impl;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCloseParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePreConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DataSourceTestParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceUpdateParam;
import ai.chat2db.server.domain.api.param.datasource.DatabaseQueryAllParam;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.DatabaseService;
import ai.chat2db.server.domain.api.service.ObjectSearchSyncService;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.core.converter.DataSourceConverter;
import ai.chat2db.server.domain.core.converter.EnvironmentConverter;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.core.util.ProjectPermissionUtils;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DataSourceAccessDO;
import ai.chat2db.server.domain.repository.entity.DataSourceDO;
import ai.chat2db.server.domain.repository.entity.ProjectAccessDO;
import ai.chat2db.server.domain.repository.mapper.DataSourceAccessMapper;
import ai.chat2db.server.domain.repository.mapper.DataSourceCustomMapper;
import ai.chat2db.server.domain.repository.mapper.DataSourceMapper;
import ai.chat2db.server.domain.repository.mapper.ProjectAccessMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.tools.common.exception.PermissionDeniedBusinessException;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasyCollectionUtils;
import ai.chat2db.server.tools.common.util.EasySqlUtils;
import ai.chat2db.spi.config.DBConfig;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.DataSourceConnect;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.model.KeyValue;
import ai.chat2db.spi.sql.Chat2DBContext;
import ai.chat2db.spi.sql.ConnectionPool;
import ai.chat2db.spi.sql.IDriverManager;
import ai.chat2db.spi.sql.SQLExecutor;
import ai.chat2db.spi.util.JdbcUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author moji
 * @version DataSourceCoreServiceImpl.java, v 0.1 September 23, 2022 15:51 moji Exp $
 * @date 2022/09/23
 */
@Slf4j
@Service
public class DataSourceServiceImpl implements DataSourceService {
    private DataSourceMapper getMapper() {
        return Dbutils.getMapper(DataSourceMapper.class);
    }

    @Autowired
    private DataSourceConverter dataSourceConverter;

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ObjectSearchSyncService objectSearchSyncService;


    private DataSourceCustomMapper getCustomMapper() {
        return Dbutils.getMapper(DataSourceCustomMapper.class);
    }
    @Resource
    private EnvironmentConverter environmentConverter;
    private DataSourceAccessMapper getAccessMapper() {
        return Dbutils.getMapper(DataSourceAccessMapper.class);
    }

    private ProjectAccessMapper getProjectAccessMapper() {
        return Dbutils.getMapper(ProjectAccessMapper.class);
    }

    @Override
    public DataResult<Long> createWithPermission(DataSourceCreateParam param) {
        Long projectId = resolveProjectId(param.getProjectId());
        if (!PermissionUtils.hasDeskTopOrAdminPermission()
            && !ProjectPermissionUtils.hasProjectTeamAdminPermission(projectId)) {
            throw new PermissionDeniedBusinessException();
        }
        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        dataSourceDO.setUserId(ContextUtils.getUserId());
        //dataSourceDO.setExtendInfo(null);

        dataSourceDO.setProjectId(projectId);
        getMapper().insert(dataSourceDO);
        preWarmingData(dataSourceDO.getId());
        objectSearchSyncService.requestSync(dataSourceDO.getId());
        return DataResult.of(dataSourceDO.getId());
    }

    private void preWarmingData(Long dataSourceId) {
        DataResult<DataSource> dataResult = queryById(dataSourceId);
        if (dataResult.success() && dataResult.getData() != null) {
            DataSource dataSource = dataResult.getData();
            DriverConfig driverConfig = dataSource.getDriverConfig();
            if (driverConfig == null || StringUtils.isBlank(driverConfig.getJdbcDriver())) {
                return;
            }
            try (Connection connection = IDriverManager.getConnection(dataSource.getUrl(), dataSource.getUserName(),
                    dataSource.getPassword(), dataSource.getDriverConfig(), dataSource.getExtendMap())) {
                DatabaseQueryAllParam databaseQueryAllParam = new DatabaseQueryAllParam();
                databaseQueryAllParam.setDataSourceId(dataSourceId);
                databaseQueryAllParam.setConnection(connection);
                databaseQueryAllParam.setDbType(dataSource.getType());
                databaseQueryAllParam.setRefresh(true);
                databaseService.queryAll(databaseQueryAllParam);
            } catch (Exception e) {
                log.error("preWarmingData error", e);
            }
        }
    }

    @Override
    public DataResult<Long> updateWithPermission(DataSourceUpdateParam param) {
        PermissionUtils.checkDeskTopOrAdmin();
        queryExistent(param.getId(), null);

        JdbcUtils.removePropertySameAsDefault(param.getDriverConfig());
        DataSourceDO dataSourceDO = dataSourceConverter.param2do(param);
        dataSourceDO.setGmtModified(DateUtil.date());
        dataSourceDO.setProjectId(resolveProjectId(param.getProjectId()));
        getMapper().updateById(dataSourceDO);
        ConnectionPool.removeConnection(param.getId());
        objectSearchSyncService.requestSync(dataSourceDO.getId());
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        DataSourceDO existing = getMapper().selectById(id);
        if (existing == null) {
            throw new DataNotFoundException();
        }
        checkReadPermission(id);
        if (!PermissionUtils.hasDeskTopOrAdminPermission()
            && !ProjectPermissionUtils.hasProjectTeamAdminPermission(existing.getProjectId())) {
            throw new PermissionDeniedBusinessException();
        }

        getMapper().deleteById(id);

        LambdaQueryWrapper<DataSourceAccessDO> dataSourceAccessQueryWrapper = new LambdaQueryWrapper<>();
        dataSourceAccessQueryWrapper.eq(DataSourceAccessDO::getDataSourceId, id)
        ;
        getAccessMapper().delete(dataSourceAccessQueryWrapper);
        objectSearchSyncService.removeDataSource(id);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<DataSource> queryById(Long id) {
        DataSourceDO dataSourceDO = getMapper().selectById(id);
        return DataResult.of(dataSourceConverter.do2dto(dataSourceDO));
    }

    @Override
    public DataResult<DataSource> queryExistent(Long id, DataSourceSelector selector) {
        DataResult<DataSource> dataResult = queryById(id);
        if (dataResult.getData() == null) {
            throw new DataNotFoundException();
        }
        checkReadPermission(id);

        fillData(Lists.newArrayList(dataResult.getData()), selector);

        return dataResult;
    }

    @Override
    public DataResult<Long> copyByIdWithPermission(Long id) {
        PermissionUtils.checkDeskTopOrAdmin();
        queryExistent(id, null);

        DataSourceDO dataSourceDO = getMapper().selectById(id);
        dataSourceDO.setId(null);
        String alias = dataSourceDO.getAlias() + "Copy";
        dataSourceDO.setAlias(alias);
        dataSourceDO.setGmtCreate(DateUtil.date());
        dataSourceDO.setGmtModified(DateUtil.date());
        getMapper().insert(dataSourceDO);
        cloneProjectRelation(id, dataSourceDO.getId());
        objectSearchSyncService.requestSync(dataSourceDO.getId());
        return DataResult.of(dataSourceDO.getId());
    }

    @Override
    public PageResult<DataSource> queryPage(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LambdaQueryWrapper<DataSourceDO> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper.like(DataSourceDO::getAlias, "%" + param.getSearchKey() + "%")
                    .or()
                    .like(DataSourceDO::getUrl, "%" + param.getSearchKey() + "%"));
        }
        Integer start = param.getPageNo();
        Integer offset = param.getPageSize();
        Page<DataSourceDO> page = new Page<>(start, offset);
        IPage<DataSourceDO> iPage = getMapper().selectPage(page, queryWrapper);
        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);

        return PageResult.of(dataSources, iPage.getTotal(), param);
    }

    @Override
    public PageResult<DataSource> queryPageWithPermission(DataSourcePageQueryParam param, DataSourceSelector selector) {
        LoginUser loginUser = ContextUtils.getLoginUser();

        IPage<DataSourceDO> iPage = getCustomMapper().selectPageWithPermission(
                new Page<>(param.getPageNo(), param.getPageSize()),
                BooleanUtils.isTrue(loginUser.getAdmin()), loginUser.getId(), param.getSearchKey(),
                EasySqlUtils.orderBy(param.getOrderByList()));

        List<DataSource> dataSources = dataSourceConverter.do2dto(iPage.getRecords());

        fillData(dataSources, selector);

        return PageResult.of(dataSources, iPage.getTotal(), param);

    }

    @Override
    public ListResult<DataSource> queryByIds(List<Long> ids) {
        return listQuery(ids, null);
    }

    @Override
    public ListResult<DataSource> listQuery(List<Long> idList, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(idList)) {
            return ListResult.empty();
        }
        List<DataSourceDO> dataList = getMapper().selectBatchIds(idList);
        List<DataSource> list = dataSourceConverter.do2dto(dataList);

        fillData(list, selector);
        return ListResult.of(list);
    }

    @Override
    public ActionResult preConnect(DataSourcePreConnectParam param) {
        DataSourceTestParam testParam
                = dataSourceConverter.param2param(param);
        DriverConfig driverConfig = testParam.getDriverConfig();
        if (driverConfig == null || !driverConfig.notEmpty()) {
            driverConfig = Chat2DBContext.getDefaultDriverConfig(param.getType());
        }
        DataSourceConnect dataSourceConnect = JdbcUtils.testConnect(testParam.getUrl(), testParam.getHost(),
                testParam.getPort(),
                testParam.getUsername(), testParam.getPassword(), testParam.getDbType(),
                driverConfig, param.getSsh(), KeyValue.toMap(param.getExtendInfo()));
        if (BooleanUtils.isNotTrue(dataSourceConnect.getSuccess())) {
            return ActionResult.fail(dataSourceConnect.getMessage(), dataSourceConnect.getDescription(),
                    dataSourceConnect.getErrorDetail());
        }
        return ActionResult.isSuccess();
    }

    @Override
    public ListResult<Database> connect(Long id) {
        DatabaseQueryAllParam queryAllParam = new DatabaseQueryAllParam();
        queryAllParam.setDataSourceId(id);
        List<Database> databases = Chat2DBContext.getMetaData().databases(Chat2DBContext.getConnection());
        return ListResult.of(databases);
    }

    @Override
    public ActionResult close(Long id) {
        ConnectionPool.removeConnection(id);
        return ActionResult.isSuccess();
    }

    private void fillData(List<DataSource> list, DataSourceSelector selector) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }

        fillProject(list);

        if (selector == null) {
            return;
        }

        fillEnvironment(list, selector);

        fillSupportDatabase(list);
    }

    private void checkReadPermission(Long dataSourceId) {
        LoginUser loginUser = ContextUtils.getLoginUser();
        Integer count = getCustomMapper().countReadable(BooleanUtils.isTrue(loginUser.getAdmin()), loginUser.getId(), dataSourceId);
        if (count == null || count <= 0) {
            throw new PermissionDeniedBusinessException();
        }
    }

    private void fillSupportDatabase(List<DataSource> list) {

        if(CollectionUtils.isEmpty(list)) {
            return;
        }
        for (DataSource dataSource:list) {
            String type = dataSource.getType();
            if(StringUtils.isNotBlank(type)) {
                DBConfig config = Chat2DBContext.getDBConfig(type);
                if(config != null) {
                    dataSource.setSupportDatabase(config.isSupportDatabase());
                    dataSource.setSupportSchema(config.isSupportSchema());
                }
            }
        }
    }


    private void fillEnvironment(List<DataSource> list, DataSourceSelector selector) {
        if (BooleanUtils.isNotTrue(selector.getEnvironment())) {
            return;
        }
        List<Long> environmentIds = EasyCollectionUtils.toList(list, DataSource::getEnvironmentId).stream()
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (CollectionUtils.isEmpty(environmentIds)) {
            return;
        }
        Map<Long, Environment> environmentMap = new HashMap<>();
        environmentConverter
            .do2dto(
                Dbutils.getMapper(ai.chat2db.server.domain.repository.mapper.EnvironmentMapper.class)
                    .selectBatchIds(environmentIds)
            )
            .forEach(environment -> environmentMap.put(environment.getId(), environment));

        for (DataSource dataSource : list) {
            Long environmentId = dataSource.getEnvironmentId();
            if (environmentId == null) {
                continue;
            }
            Environment environment = dataSource.getEnvironment();
            if (environment == null) {
                environment = new Environment();
                environment.setId(environmentId);
                dataSource.setEnvironment(environment);
            }
            Environment detail = environmentMap.get(environmentId);
            if (detail != null) {
                environmentConverter.add(environment, detail);
            }
            if (StringUtils.isBlank(environment.getName())) {
                environment.setName("Environment " + environmentId);
            }
            if (StringUtils.isBlank(environment.getShortName())) {
                environment.setShortName(environment.getName());
            }
            if (StringUtils.isBlank(environment.getColor())) {
                environment.setColor("BLUE");
            }
        }
    }

    private void fillProject(List<DataSource> list) {
        if (CollectionUtils.isEmpty(list) || ContextUtils.queryLoginUser() == null) {
            return;
        }
        List<Long> projectIds = EasyCollectionUtils.toList(list, DataSource::getProjectId)
            .stream()
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (CollectionUtils.isEmpty(projectIds)) {
            return;
        }
        Map<Long, Project> projectMap = projectService.queryList(projectIds).stream()
            .collect(java.util.stream.Collectors.toMap(Project::getId, project -> project));
        LambdaQueryWrapper<ProjectAccessDO> accessQueryWrapper = new LambdaQueryWrapper<>();
        accessQueryWrapper.in(ProjectAccessDO::getProjectId, projectIds);
        List<ProjectAccessDO> projectAccessList = getProjectAccessMapper().selectList(accessQueryWrapper);
        java.util.Set<Long> sharedProjectIds = projectAccessList.stream()
            .map(ProjectAccessDO::getProjectId)
            .collect(java.util.stream.Collectors.toSet());

        for (DataSource dataSource : list) {
            Long projectId = dataSource.getProjectId();
            if (projectId == null) {
                dataSource.setAccessScope("PERSONAL");
                continue;
            }
            Project project = projectMap.get(projectId);
            if (project != null) {
                dataSource.setProjectId(projectId);
                dataSource.setProjectName(project.getName());
            }
            dataSource.setAccessScope(sharedProjectIds.contains(projectId) ? "PROJECT" : "PERSONAL");
        }
    }

    private Long resolveProjectId(Long projectId) {
        if (projectId != null) {
            projectService.query(projectId);
        }
        return projectId;
    }

    private void cloneProjectRelation(Long sourceDataSourceId, Long targetDataSourceId) {
        if (ContextUtils.queryLoginUser() == null) {
            return;
        }
        DataSourceDO source = getMapper().selectById(sourceDataSourceId);
        if (source == null || source.getProjectId() == null) {
            return;
        }
        DataSourceDO target = new DataSourceDO();
        target.setId(targetDataSourceId);
        target.setProjectId(source.getProjectId());
        getMapper().updateById(target);
    }

}
