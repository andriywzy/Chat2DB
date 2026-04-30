package ai.chat2db.server.web.start.service.sso;

import ai.chat2db.server.domain.api.model.Config;
import ai.chat2db.server.domain.api.param.SystemConfigParam;
import ai.chat2db.server.domain.api.service.ConfigService;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.web.start.config.sso.OidcResolvedConfig;
import ai.chat2db.server.web.start.config.sso.SsoAuthMode;
import ai.chat2db.server.web.start.controller.sso.request.SsoAdminConfigRequest;
import ai.chat2db.server.web.start.controller.sso.vo.OidcPublicConfigVO;
import ai.chat2db.server.web.start.controller.sso.vo.SsoAdminConfigVO;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class SsoConfigService {

    public static final String SSO_AUTH_MODE = "SSO_AUTH_MODE";
    public static final String OIDC_ENABLED = "OIDC_ENABLED";
    public static final String OIDC_ISSUER_URI = "OIDC_ISSUER_URI";
    public static final String OIDC_CLIENT_ID = "OIDC_CLIENT_ID";
    public static final String OIDC_CLIENT_SECRET = "OIDC_CLIENT_SECRET";
    public static final String OIDC_SCOPES = "OIDC_SCOPES";
    public static final String OIDC_AUTHORIZATION_URI = "OIDC_AUTHORIZATION_URI";
    public static final String OIDC_TOKEN_URI = "OIDC_TOKEN_URI";
    public static final String OIDC_USERINFO_URI = "OIDC_USERINFO_URI";
    public static final String OIDC_JWK_SET_URI = "OIDC_JWK_SET_URI";
    public static final String OIDC_REDIRECT_URI = "OIDC_REDIRECT_URI";
    public static final String OIDC_USERNAME_CLAIM = "OIDC_USERNAME_CLAIM";
    public static final String OIDC_EMAIL_CLAIM = "OIDC_EMAIL_CLAIM";
    public static final String OIDC_NAME_CLAIM = "OIDC_NAME_CLAIM";
    public static final String OIDC_GROUPS_CLAIM = "OIDC_GROUPS_CLAIM";
    public static final String OIDC_LOGOUT_REDIRECT_URI = "OIDC_LOGOUT_REDIRECT_URI";

    private final ConfigService configService;

    public SsoConfigService(ConfigService configService) {
        this.configService = configService;
    }

    public SsoAdminConfigVO getAdminConfig() {
        SsoAdminConfigVO vo = new SsoAdminConfigVO();
        vo.setEnabled(Boolean.parseBoolean(readValue(OIDC_ENABLED, "false")));
        vo.setAuthMode(readValue(SSO_AUTH_MODE, SsoAuthMode.LOCAL_AND_OIDC.name()));
        vo.setIssuerUri(readValue(OIDC_ISSUER_URI, ""));
        vo.setClientId(readValue(OIDC_CLIENT_ID, ""));
        vo.setClientSecret(readValue(OIDC_CLIENT_SECRET, ""));
        vo.setScopes(readValue(OIDC_SCOPES, "openid profile email"));
        vo.setAuthorizationUri(readValue(OIDC_AUTHORIZATION_URI, ""));
        vo.setTokenUri(readValue(OIDC_TOKEN_URI, ""));
        vo.setUserInfoUri(readValue(OIDC_USERINFO_URI, ""));
        vo.setJwkSetUri(readValue(OIDC_JWK_SET_URI, ""));
        vo.setRedirectUri(readValue(OIDC_REDIRECT_URI, ""));
        vo.setUsernameClaim(readValue(OIDC_USERNAME_CLAIM, "preferred_username"));
        vo.setEmailClaim(readValue(OIDC_EMAIL_CLAIM, "email"));
        vo.setNameClaim(readValue(OIDC_NAME_CLAIM, "name"));
        vo.setGroupsClaim(readValue(OIDC_GROUPS_CLAIM, "groups"));
        vo.setLogoutRedirectUri(readValue(OIDC_LOGOUT_REDIRECT_URI, ""));
        return vo;
    }

    public ActionResult saveAdminConfig(SsoAdminConfigRequest request) {
        OidcResolvedConfig resolvedConfig = buildResolvedConfig(request);
        validateConfig(resolvedConfig);
        writeValue(OIDC_ENABLED, String.valueOf(Boolean.TRUE.equals(request.getEnabled())));
        writeValue(SSO_AUTH_MODE, SsoAuthMode.fromCode(request.getAuthMode()).name());
        writeValue(OIDC_ISSUER_URI, trim(request.getIssuerUri()));
        writeValue(OIDC_CLIENT_ID, trim(request.getClientId()));
        writeValue(OIDC_CLIENT_SECRET, trim(request.getClientSecret()));
        writeValue(OIDC_SCOPES, normalizeScopes(request.getScopes()));
        writeValue(OIDC_AUTHORIZATION_URI, trim(request.getAuthorizationUri()));
        writeValue(OIDC_TOKEN_URI, trim(request.getTokenUri()));
        writeValue(OIDC_USERINFO_URI, trim(request.getUserInfoUri()));
        writeValue(OIDC_JWK_SET_URI, trim(request.getJwkSetUri()));
        writeValue(OIDC_REDIRECT_URI, trim(request.getRedirectUri()));
        writeValue(OIDC_USERNAME_CLAIM, defaultIfBlank(request.getUsernameClaim(), "preferred_username"));
        writeValue(OIDC_EMAIL_CLAIM, defaultIfBlank(request.getEmailClaim(), "email"));
        writeValue(OIDC_NAME_CLAIM, defaultIfBlank(request.getNameClaim(), "name"));
        writeValue(OIDC_GROUPS_CLAIM, defaultIfBlank(request.getGroupsClaim(), "groups"));
        writeValue(OIDC_LOGOUT_REDIRECT_URI, trim(request.getLogoutRedirectUri()));
        return ActionResult.isSuccess();
    }

    public OidcPublicConfigVO getPublicConfig() {
        OidcResolvedConfig config = buildResolvedConfig(getAdminConfig());
        boolean oidcEnabled = isPublicLoginEnabled(config);
        return OidcPublicConfigVO.builder()
            .enabled(oidcEnabled)
            .authMode(config.getAuthMode().name())
            .allowLocalLogin(config.getAuthMode().allowLocalLogin())
            .authorizePath("/api/oauth/oidc/authorize")
            .buttonText("Enterprise SSO")
            .build();
    }

    public boolean isLocalLoginAllowed() {
        return buildResolvedConfig(getAdminConfig()).getAuthMode().allowLocalLogin();
    }

    public OidcResolvedConfig getResolvedConfig() {
        OidcResolvedConfig config = buildResolvedConfig(getAdminConfig());
        validateConfig(config);
        return config;
    }

    public JSONObject testConnection() {
        return testConnection(null);
    }

    public JSONObject testConnection(SsoAdminConfigRequest request) {
        OidcResolvedConfig config = request == null ? getResolvedConfig() : buildResolvedConfig(request);
        validateConfig(config);
        JSONObject result = new JSONObject();
        result.put("issuerUri", config.getIssuerUri());
        result.put("authorizationUri", config.getAuthorizationUri());
        result.put("tokenUri", config.getTokenUri());
        result.put("userInfoUri", config.getUserInfoUri());
        result.put("jwkSetUri", config.getJwkSetUri());
        if (StringUtils.isNotBlank(config.getIssuerUri())) {
            String discoveryUrl = buildDiscoveryUrl(config.getIssuerUri());
            String response = HttpUtil.createGet(discoveryUrl)
                .timeout(5000)
                .charset(StandardCharsets.UTF_8)
                .execute()
                .body();
            result.put("discovery", JSON.parseObject(response));
        }
        return result;
    }

    private OidcResolvedConfig buildResolvedConfig(SsoAdminConfigVO adminConfig) {
        OidcResolvedConfig config = new OidcResolvedConfig();
        config.setEnabled(Boolean.TRUE.equals(adminConfig.getEnabled()));
        config.setAuthMode(SsoAuthMode.fromCode(adminConfig.getAuthMode()));
        config.setIssuerUri(trim(adminConfig.getIssuerUri()));
        config.setClientId(trim(adminConfig.getClientId()));
        config.setClientSecret(trim(adminConfig.getClientSecret()));
        config.setScopes(parseScopes(adminConfig.getScopes()));
        config.setAuthorizationUri(trim(adminConfig.getAuthorizationUri()));
        config.setTokenUri(trim(adminConfig.getTokenUri()));
        config.setUserInfoUri(trim(adminConfig.getUserInfoUri()));
        config.setJwkSetUri(trim(adminConfig.getJwkSetUri()));
        config.setRedirectUri(trim(adminConfig.getRedirectUri()));
        config.setUsernameClaim(defaultIfBlank(adminConfig.getUsernameClaim(), "preferred_username"));
        config.setEmailClaim(defaultIfBlank(adminConfig.getEmailClaim(), "email"));
        config.setNameClaim(defaultIfBlank(adminConfig.getNameClaim(), "name"));
        config.setGroupsClaim(defaultIfBlank(adminConfig.getGroupsClaim(), "groups"));
        config.setLogoutRedirectUri(trim(adminConfig.getLogoutRedirectUri()));
        populateDiscoveryIfNeeded(config);
        return config;
    }

    private OidcResolvedConfig buildResolvedConfig(SsoAdminConfigRequest request) {
        SsoAdminConfigVO config = new SsoAdminConfigVO();
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setAuthMode(SsoAuthMode.fromCode(request.getAuthMode()).name());
        config.setIssuerUri(request.getIssuerUri());
        config.setClientId(request.getClientId());
        config.setClientSecret(request.getClientSecret());
        config.setScopes(request.getScopes());
        config.setAuthorizationUri(request.getAuthorizationUri());
        config.setTokenUri(request.getTokenUri());
        config.setUserInfoUri(request.getUserInfoUri());
        config.setJwkSetUri(request.getJwkSetUri());
        config.setRedirectUri(request.getRedirectUri());
        config.setUsernameClaim(request.getUsernameClaim());
        config.setEmailClaim(request.getEmailClaim());
        config.setNameClaim(request.getNameClaim());
        config.setGroupsClaim(request.getGroupsClaim());
        config.setLogoutRedirectUri(request.getLogoutRedirectUri());
        return buildResolvedConfig(config);
    }

    private boolean isPublicLoginEnabled(OidcResolvedConfig config) {
        if (!config.isEnabled() || !config.getAuthMode().allowOidcLogin()) {
            return false;
        }
        try {
            validateConfig(config);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateConfig(OidcResolvedConfig config) {
        if (!config.isEnabled()) {
            return;
        }
        if (!config.getAuthMode().allowOidcLogin()) {
            return;
        }
        if (StringUtils.isBlank(config.getClientId())) {
            throw new BusinessException("sso.oidcConfig.clientIdRequired");
        }
        if (StringUtils.isBlank(config.getRedirectUri())) {
            throw new BusinessException("sso.oidcConfig.redirectUriRequired");
        }
        if (config.getScopes().isEmpty()) {
            throw new BusinessException("sso.oidcConfig.scopesRequired");
        }
        if (!config.getScopes().contains("openid")) {
            throw new BusinessException("sso.oidcConfig.openidScopeRequired");
        }
        boolean hasIssuer = StringUtils.isNotBlank(config.getIssuerUri());
        boolean hasManualEndpoints = StringUtils.isNotBlank(config.getAuthorizationUri())
            && StringUtils.isNotBlank(config.getTokenUri())
            && StringUtils.isNotBlank(config.getJwkSetUri());
        if (!hasIssuer && !hasManualEndpoints) {
            throw new BusinessException("sso.oidcConfig.discoveryOrEndpointRequired");
        }
        if (StringUtils.isBlank(config.getAuthorizationUri())) {
            throw new BusinessException("sso.oidcConfig.authorizationUriRequired");
        }
        if (StringUtils.isBlank(config.getTokenUri())) {
            throw new BusinessException("sso.oidcConfig.tokenUriRequired");
        }
        if (StringUtils.isBlank(config.getJwkSetUri())) {
            throw new BusinessException("sso.oidcConfig.jwkSetUriRequired");
        }
    }

    private void populateDiscoveryIfNeeded(OidcResolvedConfig config) {
        boolean needDiscovery = StringUtils.isBlank(config.getAuthorizationUri())
            || StringUtils.isBlank(config.getTokenUri())
            || StringUtils.isBlank(config.getUserInfoUri())
            || StringUtils.isBlank(config.getJwkSetUri());
        if (!needDiscovery || StringUtils.isBlank(config.getIssuerUri())) {
            return;
        }

        JSONObject discovery = fetchDiscovery(config.getIssuerUri());
        if (StringUtils.isBlank(config.getAuthorizationUri())) {
            config.setAuthorizationUri(discovery.getString("authorization_endpoint"));
        }
        if (StringUtils.isBlank(config.getTokenUri())) {
            config.setTokenUri(discovery.getString("token_endpoint"));
        }
        if (StringUtils.isBlank(config.getUserInfoUri())) {
            config.setUserInfoUri(discovery.getString("userinfo_endpoint"));
        }
        if (StringUtils.isBlank(config.getJwkSetUri())) {
            config.setJwkSetUri(discovery.getString("jwks_uri"));
        }
    }

    private JSONObject fetchDiscovery(String issuerUri) {
        String discoveryUrl = buildDiscoveryUrl(issuerUri);
        String response = HttpUtil.createGet(discoveryUrl)
            .timeout(5000)
            .charset(StandardCharsets.UTF_8)
            .execute()
            .body();
        return JSON.parseObject(response);
    }

    private String buildDiscoveryUrl(String issuerUri) {
        String normalizedIssuer = StringUtils.removeEnd(trim(issuerUri), "/");
        if (normalizedIssuer.endsWith("/.well-known/openid-configuration")) {
            return normalizedIssuer;
        }
        return normalizedIssuer + "/.well-known/openid-configuration";
    }

    private void writeValue(String code, String content) {
        configService.createOrUpdate(SystemConfigParam.builder().code(code).content(content).build());
    }

    private String readValue(String code, String defaultValue) {
        Config config = configService.find(code).getData();
        return config == null || config.getContent() == null ? defaultValue : config.getContent();
    }

    private String trim(String value) {
        return StringUtils.trimToEmpty(value);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.defaultIfBlank(StringUtils.trimToNull(value), defaultValue);
    }

    private String normalizeScopes(String scopes) {
        List<String> items = parseScopes(scopes);
        if (items.isEmpty()) {
            return "openid profile email";
        }
        return String.join(" ", items);
    }

    private List<String> parseScopes(String scopes) {
        return Arrays.stream(StringUtils.defaultIfBlank(scopes, "").split("[,\\s]+"))
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }
}
