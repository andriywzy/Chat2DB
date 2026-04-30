package ai.chat2db.server.web.start.service.sso;

import ai.chat2db.server.domain.repository.entity.DbhubUserDO;
import ai.chat2db.server.domain.repository.entity.UserIdentityBindingDO;
import ai.chat2db.server.domain.repository.mapper.DbhubUserMapper;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.web.start.config.sso.OidcAuthenticatedProfile;
import ai.chat2db.server.web.start.config.sso.OidcResolvedConfig;
import ai.chat2db.server.web.start.config.sso.OidcStateSession;
import ai.chat2db.server.web.start.config.sso.SsoConstants;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.mapper.UserIdentityBindingMapper;
import ai.chat2db.server.domain.core.cache.MemoryCacheManage;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class OidcAuthenticationService {

    private final SsoConfigService ssoConfigService;
    private final UserIdentityBindingService userIdentityBindingService;
    private final SsoTeamSyncService ssoTeamSyncService;

    public OidcAuthenticationService(
        SsoConfigService ssoConfigService,
        UserIdentityBindingService userIdentityBindingService,
        SsoTeamSyncService ssoTeamSyncService
    ) {
        this.ssoConfigService = ssoConfigService;
        this.userIdentityBindingService = userIdentityBindingService;
        this.ssoTeamSyncService = ssoTeamSyncService;
    }

    public String buildAuthorizeUrl(String callback) {
        OidcResolvedConfig config = ssoConfigService.getResolvedConfig();
        if (!config.isEnabled() || !config.getAuthMode().allowOidcLogin()) {
            throw new BusinessException("sso.oidcDisabled");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        String nonce = UUID.randomUUID().toString().replace("-", "");
        MemoryCacheManage.put(buildStateCacheKey(state), new OidcStateSession(state, nonce, StringUtils.defaultIfBlank(callback, "/")));

        StringBuilder builder = new StringBuilder(config.getAuthorizationUri());
        builder.append(config.getAuthorizationUri().contains("?") ? "&" : "?");
        builder.append("response_type=code");
        builder.append("&client_id=").append(urlEncode(config.getClientId()));
        builder.append("&redirect_uri=").append(urlEncode(resolveRedirectUri(config)));
        builder.append("&scope=").append(urlEncode(String.join(" ", config.getScopes())));
        builder.append("&state=").append(urlEncode(state));
        builder.append("&nonce=").append(urlEncode(nonce));
        return builder.toString();
    }

    public String handleCallback(String code, String state, HttpServletRequest request) {
        OidcResolvedConfig config = ssoConfigService.getResolvedConfig();
        OidcStateSession session = MemoryCacheManage.get(buildStateCacheKey(state));
        if (session == null) {
            throw new BusinessException("sso.oidcStateExpired");
        }
        MemoryCacheManage.remove(buildStateCacheKey(state));

        JSONObject tokenResponse = exchangeToken(config, code, resolveRedirectUri(config));
        String idToken = tokenResponse.getString("id_token");
        String accessToken = tokenResponse.getString("access_token");
        if (StringUtils.isBlank(idToken)) {
            throw new BusinessException("sso.oidcInvalidIdToken");
        }

        JWTClaimsSet claims = verifyIdToken(config, idToken, session.getNonce());
        OidcAuthenticatedProfile profile = buildProfile(config, claims, accessToken);
        if (StringUtils.isBlank(profile.getSubject())) {
            throw new BusinessException("sso.oidcSubjectMissing");
        }

        DbhubUserDO user = userIdentityBindingService.syncLoginUser(profile);
        ssoTeamSyncService.syncUserTeamMembership(user.getId(), profile.getIssuer(), profile.getGroups());
        StpUtil.login(user.getId());
        return buildFinalRedirect(request, session.getCallback());
    }

    public UserIdentityBindingDO findBindingByUserId(Long userId) {
        return Dbutils.getMapper(UserIdentityBindingMapper.class).selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserIdentityBindingDO>()
                .eq(UserIdentityBindingDO::getUserId, userId)
                .eq(UserIdentityBindingDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
                .last("limit 1")
        );
    }

    private JSONObject exchangeToken(OidcResolvedConfig config, String code, String redirectUri) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("client_id", config.getClientId());
        if (StringUtils.isNotBlank(config.getClientSecret())) {
            form.put("client_secret", config.getClientSecret());
        }

        String response = HttpRequest.post(config.getTokenUri())
            .timeout(8000)
            .form(form)
            .execute()
            .body();
        return JSON.parseObject(response);
    }

    private JWTClaimsSet verifyIdToken(OidcResolvedConfig config, String idToken, String expectedNonce) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            JWKSet jwkSet = JWKSet.parse(HttpUtil.get(config.getJwkSetUri(), StandardCharsets.UTF_8));
            JWK jwk = jwt.getHeader().getKeyID() == null
                ? jwkSet.getKeys().stream().findFirst().orElse(null)
                : jwkSet.getKeyByKeyId(jwt.getHeader().getKeyID());
            if (jwk == null || !verifySignature(jwt, jwk)) {
                throw new BusinessException("sso.oidcInvalidIdToken");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!StringUtils.equals(trimSlash(config.getIssuerUri()), trimSlash(claims.getIssuer()))) {
                throw new BusinessException("sso.oidcInvalidIssuer");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(config.getClientId())) {
                throw new BusinessException("sso.oidcInvalidAudience");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.toInstant().isBefore(Instant.now().minusSeconds(30))) {
                throw new BusinessException("sso.oidcTokenExpired");
            }
            if (StringUtils.isNotBlank(expectedNonce) && !StringUtils.equals(expectedNonce, claims.getStringClaim("nonce"))) {
                throw new BusinessException("sso.oidcInvalidNonce");
            }
            return claims;
        } catch (ParseException e) {
            throw new BusinessException("sso.oidcInvalidIdToken");
        }
    }

    private boolean verifySignature(SignedJWT jwt, JWK jwk) {
        try {
            if (jwk instanceof RSAKey rsaKey) {
                return jwt.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
            }
            if (jwk instanceof ECKey ecKey) {
                return jwt.verify(new ECDSAVerifier(ecKey.toECPublicKey()));
            }
            JWSAlgorithm algorithm = jwt.getHeader().getAlgorithm();
            if (JWSAlgorithm.Family.RSA.contains(algorithm) && jwk.toRSAKey() != null) {
                return jwt.verify(new RSASSAVerifier(jwk.toRSAKey().toRSAPublicKey()));
            }
            if (JWSAlgorithm.Family.EC.contains(algorithm) && jwk.toECKey() != null) {
                return jwt.verify(new ECDSAVerifier(jwk.toECKey().toECPublicKey()));
            }
            return false;
        } catch (JOSEException e) {
            return false;
        }
    }

    private OidcAuthenticatedProfile buildProfile(OidcResolvedConfig config, JWTClaimsSet claims, String accessToken) {
        JSONObject baseProfile = new JSONObject();
        for (Map.Entry<String, Object> entry : claims.getClaims().entrySet()) {
            baseProfile.put(entry.getKey(), entry.getValue());
        }
        if (StringUtils.isNotBlank(config.getUserInfoUri()) && StringUtils.isNotBlank(accessToken)) {
            String userInfoResponse = HttpRequest.get(config.getUserInfoUri())
                .timeout(8000)
                .bearerAuth(accessToken)
                .execute()
                .body();
            JSONObject userInfo = JSON.parseObject(userInfoResponse);
            if (userInfo != null) {
                for (String key : userInfo.keySet()) {
                    if (!baseProfile.containsKey(key) || baseProfile.get(key) == null) {
                        baseProfile.put(key, userInfo.get(key));
                    }
                }
            }
        }

        OidcAuthenticatedProfile profile = new OidcAuthenticatedProfile();
        profile.setIssuer(StringUtils.defaultIfBlank(baseProfile.getString("iss"), claims.getIssuer()));
        profile.setSubject(firstNonBlank(baseProfile.getString("sub"), getStringClaim(claims, "sub")));
        profile.setUserName(extractClaimValue(baseProfile, config.getUsernameClaim()));
        profile.setEmail(extractClaimValue(baseProfile, config.getEmailClaim()));
        profile.setDisplayName(extractClaimValue(baseProfile, config.getNameClaim()));
        profile.setGroups(extractGroups(baseProfile.get(config.getGroupsClaim())));
        profile.setRawProfileSnapshot(JSON.toJSONString(baseProfile));
        return profile;
    }

    private List<String> extractGroups(Object claimValue) {
        if (claimValue == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (claimValue instanceof JSONArray jsonArray) {
            for (Object item : jsonArray) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
        } else if (claimValue instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && StringUtils.isNotBlank(String.valueOf(item))) {
                    result.add(String.valueOf(item));
                }
            }
        } else {
            String groups = String.valueOf(claimValue);
            for (String item : groups.split("[,\\s]+")) {
                if (StringUtils.isNotBlank(item)) {
                    result.add(item.trim());
                }
            }
        }
        return result.stream().distinct().toList();
    }

    private String extractClaimValue(JSONObject object, String claimName) {
        if (object == null || StringUtils.isBlank(claimName)) {
            return null;
        }
        Object value = object.get(claimName);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private String getStringClaim(JWTClaimsSet claims, String claimName) {
        try {
            return claims.getStringClaim(claimName);
        } catch (ParseException e) {
            return null;
        }
    }

    private String buildStateCacheKey(String state) {
        return "oidc_state_" + state;
    }

    private String resolveRedirectUri(OidcResolvedConfig config) {
        return config.getRedirectUri();
    }

    private String buildFinalRedirect(HttpServletRequest request, String callback) {
        String normalizedCallback = StringUtils.defaultIfBlank(callback, "/");
        if (normalizedCallback.startsWith("http://") || normalizedCallback.startsWith("https://")) {
            return normalizedCallback;
        }
        String origin = request.getScheme() + "://" + request.getServerName()
            + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? "" : ":" + request.getServerPort());
        if (!normalizedCallback.startsWith("/")) {
            normalizedCallback = "/" + normalizedCallback;
        }
        return origin + normalizedCallback;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String trimSlash(String value) {
        return StringUtils.removeEnd(StringUtils.trimToEmpty(value), "/");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
