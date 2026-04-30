package ai.chat2db.server.web.start.config.sso;

import java.util.List;
import lombok.Data;

@Data
public class OidcResolvedConfig {

    private boolean enabled;

    private SsoAuthMode authMode = SsoAuthMode.LOCAL_AND_OIDC;

    private String issuerUri;

    private String clientId;

    private String clientSecret;

    private List<String> scopes;

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
