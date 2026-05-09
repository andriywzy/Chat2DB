package ai.chat2db.server.web.api.controller.data.source;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.param.ConsoleCloseParam;
import ai.chat2db.server.domain.api.param.ConsoleConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceCreateParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePageQueryParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourcePreConnectParam;
import ai.chat2db.server.domain.api.param.datasource.DataSourceSelector;
import ai.chat2db.server.domain.api.param.datasource.DataSourceUpdateParam;
import ai.chat2db.server.domain.api.service.ConsoleService;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.core.util.ProjectPermissionUtils;
import ai.chat2db.server.tools.common.exception.ConnectionException;
import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.spi.model.Database;
import ai.chat2db.spi.ssh.SSHManager;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.web.api.aspect.ConnectionInfoAspect;
import ai.chat2db.server.web.api.controller.data.source.converter.DataSourceWebConverter;
import ai.chat2db.server.web.api.controller.data.source.converter.SSHWebConverter;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleCloseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.ConsoleConnectRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceAttachRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCloneRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCloseRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceCreateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceExportRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceImportItemRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceImportRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceQueryRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceTestRequest;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceUpdateRequest;
import ai.chat2db.server.web.api.controller.data.source.request.SSHTestRequest;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceImportResultVO;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceTemplateVO;
import ai.chat2db.server.web.api.controller.data.source.vo.DataSourceVO;
import ai.chat2db.server.web.api.controller.data.source.vo.DatabaseVO;
import com.jcraft.jsch.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Database connection class
 *
 * @author moji
 * @version ConnectionController.java, v 0.1 September 16, 2022 14:07 moji Exp $
 * @date 2022/09/16
 */
@ConnectionInfoAspect
@RequestMapping("/api/connection")
@RestController
@Slf4j
public class DataSourceController {

    private static final DataSourceSelector DATA_SOURCE_SELECTOR = DataSourceSelector.builder()
        .environment(Boolean.TRUE)
        .build();
    private static final String CONNECTION_TEMPLATE = "chat2db.datasource.template";
    private static final String CONNECTION_TEMPLATE_VERSION = "1.0";

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private ConsoleService consoleService;

    @Autowired
    private DataSourceWebConverter dataSourceWebConverter;

    @Autowired
    private SSHWebConverter sshWebConverter;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private ProjectService projectService;

    /**
     * Database connection test
     *
     * @param request
     * @return
     */
    @RequestMapping("/datasource/pre_connect")
    public ActionResult preConnect(@RequestBody DataSourceTestRequest request) {
        DataSourcePreConnectParam param = dataSourceWebConverter.testRequest2param(request);
        return dataSourceService.preConnect(param);
    }

    /**
     * Database connection test
     *
     * @param request
     * @return
     */
    @RequestMapping("/ssh/pre_connect")
    public ActionResult sshConnect(@RequestBody SSHTestRequest request) {
        Session session = null;
        try {
            session = SSHManager.getSSHSession(sshWebConverter.toInfo(request));
        } catch (Exception e) {
            log.error("sshConnect error", e);
            throw new ConnectionException("connection.ssh.error", null, e);
        } finally {
            if (session != null) {
                session.disconnect();
            }
        }
        return ActionResult.isSuccess();
    }

    /**
     * Database connection
     *
     * @param request
     * @return
     */
    @GetMapping("/datasource/connect")
    public ListResult<DatabaseVO> attach(@Valid @NotNull DataSourceAttachRequest request) {
        ListResult<Database> databaseDTOListResult = dataSourceService.connect(request.getId());
        List<DatabaseVO> databaseVOS = dataSourceWebConverter.databaseDto2vo(databaseDTOListResult.getData());
        return ListResult.of(databaseVOS);
    }

    /**
     * Close database connection
     *
     * @param request
     * @return
     */
    @GetMapping("/datasource/close")
    public ActionResult close(@Valid @NotNull DataSourceCloseRequest request) {
        return dataSourceService.close(request.getId());
    }

    /**
     * Console connection
     *
     * @param request
     * @return
     */
    @GetMapping("/console/connect")
    public ActionResult connect(@Valid @NotNull ConsoleConnectRequest request) {
        ConsoleConnectParam consoleConnectParam = dataSourceWebConverter.request2connectParam(request);
        return consoleService.createConsole(consoleConnectParam);
    }

    /**
     * Close the Console connection
     *
     * @param request
     * @return
     */
    @GetMapping("/console/close")
    public ActionResult closeConsole(@Valid @NotNull ConsoleCloseRequest request) {
        ConsoleCloseParam closeParam = dataSourceWebConverter.request2closeParam(request);
        return consoleService.closeConsole(closeParam);
    }

    /**
     * Query the database connection I established
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @GetMapping("/datasource/list")
    public WebPageResult<DataSourceVO> list(DataSourceQueryRequest request) {
        DataSourcePageQueryParam param = dataSourceWebConverter.queryReq2param(request);
        PageResult<DataSource> result = dataSourceService.queryPageWithPermission(param, DATA_SOURCE_SELECTOR);
        List<DataSourceVO> dataSourceVOS = dataSourceWebConverter.dto2vo(result.getData());
        fillManagePermission(dataSourceVOS);
        return WebPageResult.of(dataSourceVOS, result.getTotal(), result.getPageNo(), result.getPageSize());
    }

    /**
     * Get connection content
     *
     * @param id
     * @return
     */
    @GetMapping("/datasource/{id}")
    public DataResult<DataSourceVO> queryById(@PathVariable("id") Long id) {
        DataResult<DataSource> dataResult = dataSourceService.queryExistent(id, DATA_SOURCE_SELECTOR);
        DataSourceVO dataSourceVO = dataSourceWebConverter.dto2vo(dataResult.getData());
        if (StringUtils.equalsIgnoreCase(dataSourceVO.getType(), "REDIS")
            && StringUtils.isBlank(dataSourceVO.getUser())
            && StringUtils.isNotBlank(dataSourceVO.getPassword())) {
            dataSourceVO.setAuthenticationType("3");
        } else if (StringUtils.isNotBlank(dataSourceVO.getUser())) {
            dataSourceVO.setAuthenticationType("1");
        } else {
            dataSourceVO.setAuthenticationType("2");
        }
        dataSourceVO.setCanManage(PermissionUtils.hasDeskTopOrAdminPermission()
            || ProjectPermissionUtils.hasProjectTeamAdminPermission(dataSourceVO.getProjectId()));
        return DataResult.of(dataSourceVO);
    }

    /**
     * save connection
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/create")
    public DataResult<Long> create(@RequestBody DataSourceCreateRequest request) {
        DataSourceCreateParam param = dataSourceWebConverter.createReq2param(request);
        return dataSourceService.createWithPermission(param);
    }

    /**
     * Update connection
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/datasource/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public DataResult<Long> update(@RequestBody DataSourceUpdateRequest request) {
        DataSourceUpdateParam param = dataSourceWebConverter.updateReq2param(request);
        return dataSourceService.updateWithPermission(param);
    }

    /**
     *  clone connection
     *
     * @param request
     * @return
     */
    @PostMapping("/datasource/clone")
    public DataResult<Long> copy(@RequestBody DataSourceCloneRequest request) {
        return dataSourceService.copyByIdWithPermission(request.getId());
    }

    /**
     * Delete connection
     *
     * @param id
     * @return
     */
    @DeleteMapping("/datasource/{id}")
    public ActionResult delete(@PathVariable Long id) {
        return dataSourceService.deleteWithPermission(id);
    }

    @GetMapping("/datasource/template")
    public DataResult<DataSourceTemplateVO> template() {
        DataSourceTemplateVO result = new DataSourceTemplateVO();
        result.setVersion(CONNECTION_TEMPLATE_VERSION);
        result.setTemplate(CONNECTION_TEMPLATE);
        result.setExportedAt(LocalDateTime.now().toString());
        result.setConnections(List.of(buildSampleTemplateItem()));
        return DataResult.of(result);
    }

    @PostMapping("/datasource/export")
    public DataResult<DataSourceTemplateVO> export(@RequestBody DataSourceExportRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new ParamBusinessException();
        }
        List<DataSourceImportItemRequest> connections = new ArrayList<>();
        for (Long id : request.getIds()) {
            DataSource dataSource = dataSourceService.queryExistent(id, DATA_SOURCE_SELECTOR).getData();
            connections.add(toImportItem(dataSource));
        }
        DataSourceTemplateVO result = new DataSourceTemplateVO();
        result.setVersion(CONNECTION_TEMPLATE_VERSION);
        result.setTemplate(CONNECTION_TEMPLATE);
        result.setExportedAt(LocalDateTime.now().toString());
        result.setConnections(connections);
        return DataResult.of(result);
    }

    @PostMapping("/datasource/import")
    public DataResult<DataSourceImportResultVO> importConnections(@RequestBody DataSourceImportRequest request) {
        PermissionUtils.checkDeskTopOrAdmin();
        if (request == null || request.getConnections() == null || request.getConnections().isEmpty()) {
            throw new ParamBusinessException();
        }
        List<Environment> environments = environmentService.queryList().getData();
        List<Project> projects = projectService.queryList().getData();
        Map<Long, Environment> environmentById = environments.stream()
            .filter(environment -> environment.getId() != null)
            .collect(Collectors.toMap(Environment::getId, Function.identity(), (left, right) -> left));
        Map<String, Environment> environmentByName = environments.stream()
            .filter(environment -> StringUtils.isNotBlank(environment.getName()))
            .collect(Collectors.toMap(environment -> environment.getName().trim().toLowerCase(), Function.identity(),
                (left, right) -> left));
        Map<String, Environment> environmentByShortName = environments.stream()
            .filter(environment -> StringUtils.isNotBlank(environment.getShortName()))
            .collect(Collectors.toMap(environment -> environment.getShortName().trim().toLowerCase(), Function.identity(),
                (left, right) -> left));
        Map<Long, Project> projectById = projects.stream()
            .filter(project -> project.getId() != null)
            .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
        Map<String, Project> projectByName = projects.stream()
            .filter(project -> StringUtils.isNotBlank(project.getName()))
            .collect(Collectors.toMap(project -> project.getName().trim().toLowerCase(), Function.identity(),
                (left, right) -> left));

        DataSourceImportResultVO result = new DataSourceImportResultVO();
        result.setTotal(request.getConnections().size());
        for (DataSourceImportItemRequest item : request.getConnections()) {
            try {
                DataSourceCreateParam param = new DataSourceCreateParam();
                param.setAlias(item.getAlias());
                param.setUrl(item.getUrl());
                param.setUserName(item.getUser());
                param.setPassword(item.getPassword());
                param.setType(item.getType());
                param.setHost(item.getHost());
                param.setPort(item.getPort());
                param.setSsh(item.getSsh());
                param.setSid(item.getSid());
                param.setDriver(item.getDriver());
                param.setJdbc(item.getJdbc());
                param.setExtendInfo(item.getExtendInfo());
                param.setDriverConfig(item.getDriverConfig());
                param.setServiceName(item.getServiceName());
                param.setServiceType(item.getServiceType());
                param.setEnvironmentId(resolveEnvironmentId(item, request.getDefaultEnvironmentId(), environments,
                    environmentById, environmentByName, environmentByShortName));
                param.setProjectId(resolveProjectId(item, projectById, projectByName));
                Long createdId = dataSourceService.createWithPermission(param).getData();
                result.getCreatedIds().add(createdId);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                String alias = StringUtils.defaultIfBlank(item.getAlias(), item.getUrl());
                String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
                result.getErrors().add(alias + ": " + message);
            }
        }
        result.setFailureCount(result.getTotal() - result.getSuccessCount());
        return DataResult.of(result);
    }

    private DataSourceImportItemRequest toImportItem(DataSource dataSource) {
        DataSourceImportItemRequest item = new DataSourceImportItemRequest();
        item.setAlias(dataSource.getAlias());
        item.setUrl(dataSource.getUrl());
        item.setUser(dataSource.getUserName());
        item.setPassword(dataSource.getPassword());
        item.setType(dataSource.getType());
        item.setHost(dataSource.getHost());
        item.setPort(dataSource.getPort());
        item.setSsh(dataSource.getSsh());
        item.setSid(dataSource.getSid());
        item.setDriver(dataSource.getDriver());
        item.setJdbc(dataSource.getJdbc());
        item.setExtendInfo(dataSource.getExtendInfo());
        item.setDriverConfig(dataSource.getDriverConfig());
        item.setEnvironmentId(dataSource.getEnvironmentId());
        item.setEnvironmentName(dataSource.getEnvironment() == null ? null : dataSource.getEnvironment().getName());
        item.setEnvironmentShortName(dataSource.getEnvironment() == null ? null : dataSource.getEnvironment().getShortName());
        item.setProjectId(dataSource.getProjectId());
        item.setProjectName(dataSource.getProjectName());
        item.setServiceName(dataSource.getServiceName());
        item.setServiceType(dataSource.getServiceType());
        return item;
    }

    private DataSourceImportItemRequest buildSampleTemplateItem() {
        DataSourceImportItemRequest item = new DataSourceImportItemRequest();
        item.setAlias("local-mysql");
        item.setType("MYSQL");
        item.setHost("127.0.0.1");
        item.setPort("3306");
        item.setUrl("jdbc:mysql://127.0.0.1:3306/demo");
        item.setUser("root");
        item.setPassword("your-password");
        item.setEnvironmentName("Development");
        item.setProjectName("Default Project");
        return item;
    }

    private Long resolveEnvironmentId(DataSourceImportItemRequest item, Long defaultEnvironmentId,
                                      List<Environment> environments,
                                      Map<Long, Environment> environmentById,
                                      Map<String, Environment> environmentByName,
                                      Map<String, Environment> environmentByShortName) {
        if (item.getEnvironmentId() != null && environmentById.containsKey(item.getEnvironmentId())) {
            return item.getEnvironmentId();
        }
        if (StringUtils.isNotBlank(item.getEnvironmentName())) {
            Environment environment = environmentByName.get(item.getEnvironmentName().trim().toLowerCase());
            if (environment != null) {
                return environment.getId();
            }
        }
        if (StringUtils.isNotBlank(item.getEnvironmentShortName())) {
            Environment environment = environmentByShortName.get(item.getEnvironmentShortName().trim().toLowerCase());
            if (environment != null) {
                return environment.getId();
            }
        }
        if (defaultEnvironmentId != null && environmentById.containsKey(defaultEnvironmentId)) {
            return defaultEnvironmentId;
        }
        if (environments.size() == 1) {
            return environments.get(0).getId();
        }
        throw new ParamBusinessException();
    }

    private Long resolveProjectId(DataSourceImportItemRequest item,
                                  Map<Long, Project> projectById,
                                  Map<String, Project> projectByName) {
        if (item.getProjectId() != null && projectById.containsKey(item.getProjectId())) {
            return item.getProjectId();
        }
        if (StringUtils.isNotBlank(item.getProjectName())) {
            Project project = projectByName.get(item.getProjectName().trim().toLowerCase());
            if (project != null) {
                return project.getId();
            }
        }
        return null;
    }

    private void fillManagePermission(List<DataSourceVO> dataSourceVOS) {
        Set<Long> teamAdminProjectIds = ProjectPermissionUtils.getTeamAdminProjectIds();
        for (DataSourceVO dataSourceVO : dataSourceVOS) {
            dataSourceVO.setCanManage(PermissionUtils.hasDeskTopOrAdminPermission()
                || teamAdminProjectIds.contains(dataSourceVO.getProjectId()));
        }
    }

}
