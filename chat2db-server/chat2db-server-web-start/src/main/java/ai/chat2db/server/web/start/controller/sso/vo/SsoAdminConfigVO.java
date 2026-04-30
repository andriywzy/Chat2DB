package ai.chat2db.server.web.start.controller.sso.vo;

import lombok.Data;

@Data
public class SsoAdminConfigVO {

    private Boolean enabled;

    private String authMode;

    private String issuerUri;

    private String clientId;

    private String clientSecret;

    private String scopes;

    private String authorizationUri;

    private String tokenUri;

    private String userInfoUri;

    private String jwkSetUri;

    private String redirectUri;

    private String usernameClaim;

    private String emailClaim;

    private String nameClaim;

    private String groupsClaim;

    private String logoutRedirectUri;
}
