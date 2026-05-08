package ai.chat2db.server.common.api.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditStatusEnum;
import ai.chat2db.server.domain.api.model.ConsoleAuditCreateRequest;
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
        if (throwable != null) {
            return adminAudit.actionType().getCode() + " " + adminAudit.resourceType().getCode() + " failed: " + throwable.getMessage();
        }
        return adminAudit.actionType().getCode() + " " + adminAudit.resourceType().getCode()
            + " request=" + sanitizeMap(argumentMap(args))
            + " before=" + sanitizeMap(beforeSnapshot)
            + " after=" + sanitizeMap(afterSnapshot);
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
