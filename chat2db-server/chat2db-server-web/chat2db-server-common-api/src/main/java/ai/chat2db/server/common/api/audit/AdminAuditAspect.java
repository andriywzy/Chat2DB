package ai.chat2db.server.common.api.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditResourceTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditStatusEnum;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.domain.api.model.Project;
import ai.chat2db.server.domain.api.model.Team;
import ai.chat2db.server.domain.api.model.User;
import ai.chat2db.server.domain.api.service.AuditService;
import ai.chat2db.server.domain.api.service.DataSourceService;
import ai.chat2db.server.domain.api.service.EnvironmentService;
import ai.chat2db.server.domain.api.service.ProjectService;
import ai.chat2db.server.domain.api.service.TeamService;
import ai.chat2db.server.domain.api.service.UserService;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.LogUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Slf4j
public class AdminAuditAspect {

    private static final List<String> SENSITIVE_KEYS = List.of("password", "token", "secret", "jdbc", "authorization");
    private static final Map<String, String> RESOURCE_LABELS = Map.of(
        AuditResourceTypeEnum.USER.getCode(), "用户",
        AuditResourceTypeEnum.TEAM.getCode(), "团队",
        AuditResourceTypeEnum.PROJECT.getCode(), "项目",
        AuditResourceTypeEnum.ENVIRONMENT.getCode(), "环境",
        AuditResourceTypeEnum.DATASOURCE.getCode(), "数据源",
        AuditResourceTypeEnum.TEAM_USER.getCode(), "团队成员",
        AuditResourceTypeEnum.TEAM_PROJECT.getCode(), "团队项目授权"
    );
    private static final Map<String, String> ACTION_LABELS = Map.of(
        AuditActionTypeEnum.CREATE.getCode(), "新增",
        AuditActionTypeEnum.UPDATE.getCode(), "修改",
        AuditActionTypeEnum.DELETE.getCode(), "删除",
        AuditActionTypeEnum.GRANT.getCode(), "授权"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuditService auditService;
    private final UserService userService;
    private final TeamService teamService;
    private final ProjectService projectService;
    private final EnvironmentService environmentService;
    private final DataSourceService dataSourceService;

    public AdminAuditAspect(AuditService auditService, UserService userService, TeamService teamService,
                            ProjectService projectService, EnvironmentService environmentService,
                            DataSourceService dataSourceService) {
        this.auditService = auditService;
        this.userService = userService;
        this.teamService = teamService;
        this.projectService = projectService;
        this.environmentService = environmentService;
        this.dataSourceService = dataSourceService;
    }

    @Around("@annotation(adminAudit)")
    public Object around(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
        String targetIdBefore = resolveTargetId(joinPoint.getArgs(), null);
        Map<String, Object> beforeSnapshot = snapshot(adminAudit.resourceType().getCode(), targetIdBefore);
        Throwable throwable = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            throwable = t;
            throw t;
        } finally {
            try {
                writeAudit(joinPoint, adminAudit, result, throwable, beforeSnapshot, targetIdBefore);
            } catch (Exception e) {
                log.error("write console audit error", e);
            }
        }
    }

    private void writeAudit(ProceedingJoinPoint joinPoint, AdminAudit adminAudit, Object result, Throwable throwable,
                            Map<String, Object> beforeSnapshot, String targetIdBefore) {
        String targetId = resolveTargetId(joinPoint.getArgs(), result);
        if (StringUtils.isBlank(targetId)) {
            targetId = targetIdBefore;
        }
        Map<String, Object> afterSnapshot = throwable == null
            && adminAudit.actionType() != AuditActionTypeEnum.DELETE
            ? snapshot(adminAudit.resourceType().getCode(), targetId)
            : null;
        HttpServletRequest request = currentRequest();
        LoginUser loginUser = ContextUtils.getLoginUser();
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("request", sanitizeMap(argumentMap(joinPoint)));
        payloadMap.put("before", sanitizeMap(beforeSnapshot));
        payloadMap.put("after", sanitizeMap(afterSnapshot));
        payloadMap.put("error", throwable == null ? null : throwable.getMessage());
        String payload = toJson(payloadMap);
        String summary = buildSummary(adminAudit, beforeSnapshot, afterSnapshot, joinPoint.getArgs(), throwable);
        auditService.createConsoleAudit(ConsoleAuditCreateRequest.builder()
            .actionType(adminAudit.actionType().getCode())
            .resourceType(adminAudit.resourceType().getCode())
            .operatorUserId(loginUser == null ? null : loginUser.getId())
            .operatorUserName(loginUser == null ? null : loginUser.getNickName())
            .roleCode(loginUser == null ? null : loginUser.getRoleCode())
            .targetId(targetId)
            .targetName(resolveTargetName(afterSnapshot, beforeSnapshot))
            .requestPath(request == null ? null : request.getRequestURI())
            .requestMethod(request == null ? null : request.getMethod())
            .requestId(LogUtils.getTraceId())
            .clientIp(request == null ? null : request.getRemoteAddr())
            .userAgent(request == null ? null : request.getHeader("User-Agent"))
            .status(resolveStatus(result, throwable))
            .detailSummary(summary)
            .detailPayload(payload)
            .errorMessage(throwable == null ? null : throwable.getMessage())
            .build());
    }

    private String resolveStatus(Object result, Throwable throwable) {
        if (throwable != null) {
            return AuditStatusEnum.FAILED.getCode();
        }
        if (result instanceof ActionResult actionResult && Boolean.FALSE.equals(actionResult.getSuccess())) {
            return AuditStatusEnum.FAILED.getCode();
        }
        if (result instanceof DataResult<?> dataResult && Boolean.FALSE.equals(dataResult.getSuccess())) {
            return AuditStatusEnum.FAILED.getCode();
        }
        return AuditStatusEnum.SUCCESS.getCode();
    }

    private String resolveTargetId(Object[] args, Object result) {
        if (result instanceof DataResult<?> dataResult && dataResult.getData() != null && dataResult.getData() instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof Number number) {
                return String.valueOf(number.longValue());
            }
            BeanWrapperImpl beanWrapper = new BeanWrapperImpl(arg);
            if (beanWrapper.isReadableProperty("id")) {
                Object id = beanWrapper.getPropertyValue("id");
                if (id != null) {
                    return String.valueOf(id);
                }
            }
        }
        return null;
    }

    private String resolveTargetName(Map<String, Object> afterSnapshot, Map<String, Object> beforeSnapshot) {
        Map<String, Object>[] snapshots = new Map[] {afterSnapshot, beforeSnapshot};
        for (Map<String, Object> snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            for (String key : List.of("name", "alias", "nickName", "userName", "code")) {
                Object value = snapshot.get(key);
                if (value != null) {
                    return String.valueOf(value);
                }
            }
        }
        return null;
    }

    private String buildSummary(AdminAudit adminAudit, Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot,
                                Object[] args, Throwable throwable) {
        String targetName = resolveTargetName(afterSnapshot, beforeSnapshot);
        if (throwable != null) {
            return buildFailureSummary(adminAudit, targetName, throwable);
        }
        return switch (adminAudit.resourceType()) {
            case USER -> buildUserSummary(adminAudit.actionType(), beforeSnapshot, afterSnapshot, targetName);
            case TEAM -> buildTeamSummary(adminAudit.actionType(), beforeSnapshot, afterSnapshot, targetName);
            case PROJECT -> buildProjectSummary(adminAudit.actionType(), beforeSnapshot, afterSnapshot, targetName);
            case ENVIRONMENT -> buildEnvironmentSummary(adminAudit.actionType(), beforeSnapshot, afterSnapshot, targetName);
            case DATASOURCE -> buildDataSourceSummary(adminAudit.actionType(), beforeSnapshot, afterSnapshot, targetName, args);
            case TEAM_USER -> buildTeamUserSummary(adminAudit.actionType(), args);
            case TEAM_PROJECT -> buildTeamProjectSummary(adminAudit.actionType(), args, targetName);
            default -> buildGenericSummary(adminAudit.actionType().getCode(), adminAudit.resourceType().getCode(), targetName);
        };
    }

    private String buildFailureSummary(AdminAudit adminAudit, String targetName, Throwable throwable) {
        return buildGenericSummary(adminAudit.actionType().getCode(), adminAudit.resourceType().getCode(), targetName)
            + "失败"
            + (throwable == null || StringUtils.isBlank(throwable.getMessage()) ? "" : "：" + throwable.getMessage());
    }

    private String buildUserSummary(AuditActionTypeEnum actionType, Map<String, Object> beforeSnapshot,
                                    Map<String, Object> afterSnapshot, String targetName) {
        String subject = defaultTargetName(targetName, afterSnapshot, beforeSnapshot, "用户");
        return switch (actionType) {
            case CREATE -> "新增用户 " + subject;
            case DELETE -> "删除用户 " + subject;
            case UPDATE -> buildUpdateSummary("用户", subject, beforeSnapshot, afterSnapshot,
                Map.of("roleCode", "角色", "status", "状态", "nickName", "昵称"));
            default -> buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.USER.getCode(), subject);
        };
    }

    private String buildTeamSummary(AuditActionTypeEnum actionType, Map<String, Object> beforeSnapshot,
                                    Map<String, Object> afterSnapshot, String targetName) {
        String subject = defaultTargetName(targetName, afterSnapshot, beforeSnapshot, "团队");
        return switch (actionType) {
            case CREATE -> "新增团队 " + subject;
            case DELETE -> "删除团队 " + subject;
            case UPDATE -> buildUpdateSummary("团队", subject, beforeSnapshot, afterSnapshot,
                Map.of("name", "名称", "description", "描述", "status", "状态"));
            default -> buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.TEAM.getCode(), subject);
        };
    }

    private String buildProjectSummary(AuditActionTypeEnum actionType, Map<String, Object> beforeSnapshot,
                                       Map<String, Object> afterSnapshot, String targetName) {
        String subject = defaultTargetName(targetName, afterSnapshot, beforeSnapshot, "项目");
        return switch (actionType) {
            case CREATE -> "新增项目 " + subject;
            case DELETE -> "删除项目 " + subject;
            case UPDATE -> buildUpdateSummary("项目", subject, beforeSnapshot, afterSnapshot,
                Map.of("name", "名称", "description", "描述"));
            default -> buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.PROJECT.getCode(), subject);
        };
    }

    private String buildEnvironmentSummary(AuditActionTypeEnum actionType, Map<String, Object> beforeSnapshot,
                                           Map<String, Object> afterSnapshot, String targetName) {
        String subject = defaultTargetName(targetName, afterSnapshot, beforeSnapshot, "环境");
        return switch (actionType) {
            case CREATE -> "新增环境 " + subject;
            case DELETE -> "删除环境 " + subject;
            case UPDATE -> buildUpdateSummary("环境", subject, beforeSnapshot, afterSnapshot,
                Map.of("name", "名称", "shortName", "标识", "style", "样式", "color", "颜色"));
            default -> buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.ENVIRONMENT.getCode(), subject);
        };
    }

    private String buildDataSourceSummary(AuditActionTypeEnum actionType, Map<String, Object> beforeSnapshot,
                                          Map<String, Object> afterSnapshot, String targetName, Object[] args) {
        String subject = defaultTargetName(targetName, afterSnapshot, beforeSnapshot, "数据源");
        if (actionType == AuditActionTypeEnum.CREATE && isCloneRequest(args)) {
            String sourceName = resolveCloneSourceName(args);
            return StringUtils.isNotBlank(sourceName)
                ? "克隆数据源 " + sourceName + " 为 " + subject
                : "克隆数据源为 " + subject;
        }
        return switch (actionType) {
            case CREATE -> "新增数据源 " + subject;
            case DELETE -> "删除数据源 " + subject;
            case UPDATE -> buildUpdateSummary("数据源", subject, beforeSnapshot, afterSnapshot,
                Map.of("alias", "名称", "projectId", "项目", "environmentId", "环境", "dbType", "数据库类型"));
            default -> buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.DATASOURCE.getCode(), subject);
        };
    }

    private String buildTeamUserSummary(AuditActionTypeEnum actionType, Object[] args) {
        Map<String, Object> request = argumentMap(args);
        Long userId = getLongFromRequest(request, "userId");
        Long teamId = getLongFromRequest(request, "teamId");
        List<Long> userIdList = getLongListFromRequest(request, "userIdList");
        List<Long> teamIdList = getLongListFromRequest(request, "teamIdList");
        if (actionType == AuditActionTypeEnum.GRANT) {
            if (userId != null && !teamIdList.isEmpty()) {
                String userName = findUserName(userId);
                return "将用户 " + userName + " 加入团队 " + joinNames(teamIdList.stream().map(this::findTeamName).toList());
            }
            if (teamId != null && !userIdList.isEmpty()) {
                String teamName = findTeamName(teamId);
                return "将用户 " + joinNames(userIdList.stream().map(this::findUserName).toList()) + " 加入团队 " + teamName;
            }
        }
        if (actionType == AuditActionTypeEnum.DELETE) {
            Long relationId = firstLongArg(args);
            String relationName = relationId == null ? "团队成员关系" : "团队成员关系 #" + relationId;
            return "移除" + relationName;
        }
        return buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.TEAM_USER.getCode(), null);
    }

    private String buildTeamProjectSummary(AuditActionTypeEnum actionType, Object[] args, String targetName) {
        if (actionType == AuditActionTypeEnum.GRANT) {
            Map<String, Object> request = argumentMap(args);
            Long teamId = getLongFromRequest(request, "teamId");
            List<Map<String, Object>> grants = getGrantList(request);
            if (!grants.isEmpty()) {
                List<String> summaries = new ArrayList<>();
                for (Map<String, Object> grant : grants) {
                    Long projectId = asLong(grant.get("projectId"));
                    String projectName = findProjectName(projectId);
                    List<Long> environmentIds = toLongList(grant.get("environmentIdList"));
                    StringBuilder item = new StringBuilder();
                    item.append("为团队 ").append(findTeamName(teamId)).append(" 授权项目 ").append(projectName);
                    if (!environmentIds.isEmpty()) {
                        item.append("（环境：").append(joinNames(environmentIds.stream().map(this::findEnvironmentName).toList())).append("）");
                    }
                    summaries.add(item.toString());
                }
                return String.join("；", summaries);
            }
        }
        if (actionType == AuditActionTypeEnum.DELETE) {
            String subject = StringUtils.defaultIfBlank(targetName, "项目授权");
            return "取消团队的" + subject + "授权";
        }
        return buildGenericSummary(actionType.getCode(), AuditResourceTypeEnum.TEAM_PROJECT.getCode(), targetName);
    }

    private String buildUpdateSummary(String resourceLabel, String targetName, Map<String, Object> beforeSnapshot,
                                      Map<String, Object> afterSnapshot, Map<String, String> fieldLabels) {
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldLabels.entrySet()) {
            String key = entry.getKey();
            Object before = beforeSnapshot == null ? null : beforeSnapshot.get(key);
            Object after = afterSnapshot == null ? null : afterSnapshot.get(key);
            if (sameValue(before, after)) {
                continue;
            }
            if (isBlankValue(before) && isBlankValue(after)) {
                continue;
            }
            changes.add(entry.getValue() + "改为" + readableValue(after));
        }
        if (changes.isEmpty()) {
            return "修改" + resourceLabel + " " + targetName;
        }
        return "修改" + resourceLabel + " " + targetName + " 的" + String.join("、", changes);
    }

    private String buildGenericSummary(String actionType, String resourceType, String targetName) {
        String actionLabel = ACTION_LABELS.getOrDefault(actionType, actionType);
        String resourceLabel = RESOURCE_LABELS.getOrDefault(resourceType, resourceType);
        return StringUtils.isBlank(targetName)
            ? actionLabel + resourceLabel
            : actionLabel + resourceLabel + " " + targetName;
    }

    private String defaultTargetName(String targetName, Map<String, Object> afterSnapshot,
                                     Map<String, Object> beforeSnapshot, String fallback) {
        String resolved = StringUtils.defaultIfBlank(targetName, firstNonBlank(
            getString(afterSnapshot, "name"),
            getString(afterSnapshot, "alias"),
            getString(afterSnapshot, "nickName"),
            getString(afterSnapshot, "userName"),
            getString(beforeSnapshot, "name"),
            getString(beforeSnapshot, "alias"),
            getString(beforeSnapshot, "nickName"),
            getString(beforeSnapshot, "userName")
        ));
        return StringUtils.defaultIfBlank(resolved, fallback);
    }

    private boolean isCloneRequest(Object[] args) {
        for (Object arg : args) {
            if (arg != null && StringUtils.endsWith(arg.getClass().getSimpleName(), "DataSourceCloneRequest")) {
                return true;
            }
        }
        return false;
    }

    private String resolveCloneSourceName(Object[] args) {
        Long id = null;
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            BeanWrapperImpl beanWrapper = new BeanWrapperImpl(arg);
            if (beanWrapper.isReadableProperty("id")) {
                Object value = beanWrapper.getPropertyValue("id");
                if (value instanceof Number number) {
                    id = number.longValue();
                    break;
                }
            }
        }
        if (id == null) {
            return null;
        }
        try {
            return resolveTargetName(toMap(dataSourceService.queryById(id).getData()), null);
        } catch (Exception e) {
            return null;
        }
    }

    private Long firstLongArg(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    private Long getLongFromRequest(Map<String, Object> request, String key) {
        return asLong(request == null ? null : request.get(key));
    }

    private List<Long> getLongListFromRequest(Map<String, Object> request, String key) {
        return toLongList(request == null ? null : request.get(key));
    }

    private List<Map<String, Object>> getGrantList(Map<String, Object> request) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object projectGrantList = request.get("projectGrantList");
        if (projectGrantList instanceof Collection<?> collection) {
            for (Object item : collection) {
                Map<String, Object> grant = asMap(item);
                if (grant != null) {
                    result.add(grant);
                }
            }
        }
        List<Long> projectIdList = toLongList(request.get("projectIdList"));
        for (Long projectId : projectIdList) {
            Map<String, Object> grant = new LinkedHashMap<>();
            grant.put("projectId", projectId);
            grant.put("environmentIdList", List.of());
            result.add(grant);
        }
        return result;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return convertUnknownMap(map);
        }
        if (value == null) {
            return null;
        }
        return toMap(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && StringUtils.isNotBlank(string) && StringUtils.isNumeric(string)) {
            return Long.parseLong(string);
        }
        return null;
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
            .map(this::asLong)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private boolean sameValue(Object before, Object after) {
        return StringUtils.equals(StringUtils.trimToEmpty(readableValue(before)), StringUtils.trimToEmpty(readableValue(after)));
    }

    private boolean isBlankValue(Object value) {
        return StringUtils.isBlank(readableValue(value));
    }

    private String readableValue(Object value) {
        if (value == null) {
            return "";
        }
        return switch (String.valueOf(value)) {
            case "ADMIN" -> "管理员";
            case "USER" -> "普通用户";
            case "VALID" -> "有效";
            case "INVALID" -> "无效";
            default -> String.valueOf(value);
        };
    }

    private String getString(Map<String, Object> source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String joinNames(List<String> names) {
        return names.stream().filter(StringUtils::isNotBlank).distinct().reduce((left, right) -> left + "、" + right).orElse("-");
    }

    private String findUserName(Long userId) {
        if (userId == null) {
            return "用户";
        }
        try {
            User user = userService.query(userId).getData();
            if (user == null) {
                return "用户#" + userId;
            }
            return firstNonBlank(user.getNickName(), user.getUserName(), "用户#" + userId);
        } catch (Exception e) {
            return "用户#" + userId;
        }
    }

    private String findTeamName(Long teamId) {
        if (teamId == null) {
            return "团队";
        }
        try {
            Team team = teamService.listQuery(List.of(teamId)).getData().stream().findFirst().orElse(null);
            if (team == null) {
                return "团队#" + teamId;
            }
            return StringUtils.defaultIfBlank(team.getName(), "团队#" + teamId);
        } catch (Exception e) {
            return "团队#" + teamId;
        }
    }

    private String findProjectName(Long projectId) {
        if (projectId == null) {
            return "项目";
        }
        try {
            Project project = projectService.query(projectId).getData();
            if (project == null) {
                return "项目#" + projectId;
            }
            return StringUtils.defaultIfBlank(project.getName(), "项目#" + projectId);
        } catch (Exception e) {
            return "项目#" + projectId;
        }
    }

    private String findEnvironmentName(Long environmentId) {
        if (environmentId == null) {
            return "环境";
        }
        try {
            Environment environment = environmentService.query(environmentId).getData();
            if (environment == null) {
                return "环境#" + environmentId;
            }
            return StringUtils.defaultIfBlank(environment.getName(), "环境#" + environmentId);
        } catch (Exception e) {
            return "环境#" + environmentId;
        }
    }

    private Map<String, Object> snapshot(String resourceType, String targetId) {
        if (StringUtils.isBlank(targetId)) {
            return null;
        }
        try {
            Long id = Long.valueOf(targetId);
            return switch (resourceType) {
                case "USER" -> toMap(userService.query(id).getData());
                case "TEAM" -> toMap(teamService.listQuery(List.of(id)).getData().stream().findFirst().orElse(null));
                case "PROJECT" -> toMap(projectService.query(id).getData());
                case "ENVIRONMENT" -> toMap(environmentService.query(id).getData());
                case "DATASOURCE" -> toMap(dataSourceService.queryById(id).getData());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toMap(Object object) {
        if (object == null) {
            return null;
        }
        return objectMapper.convertValue(object, new TypeReference<>() {});
    }

    private Map<String, Object> argumentMap(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            result.put(name, sanitizeObject(args[i]));
        }
        return result;
    }

    private Map<String, Object> argumentMap(Object[] args) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            result.put("arg" + i, sanitizeObject(args[i]));
        }
        return result;
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                result.put(key, "***");
                continue;
            }
            if (value instanceof Map<?, ?> innerMap) {
                result.put(key, sanitizeMap(convertUnknownMap(innerMap)));
                continue;
            }
            if (value instanceof Collection<?> collection) {
                result.put(key, collection.stream().map(this::sanitizeObject).toList());
                continue;
            }
            result.put(key, sanitizeObject(value));
        }
        return result;
    }

    private Object sanitizeObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(convertUnknownMap(map));
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeObject).toList();
        }
        if (value instanceof Date || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof String stringValue) {
            return stringValue.length() > 2000 ? stringValue.substring(0, 2000) : stringValue;
        }
        return sanitizeMap(toMap(value));
    }

    private Map<String, Object> convertUnknownMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private boolean isSensitiveKey(String key) {
        return SENSITIVE_KEYS.stream().anyMatch(sensitive -> StringUtils.containsIgnoreCase(key, sensitive));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }
}
