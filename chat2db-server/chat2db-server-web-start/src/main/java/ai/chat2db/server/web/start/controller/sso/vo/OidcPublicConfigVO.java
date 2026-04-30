package ai.chat2db.server.web.start.controller.sso.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OidcPublicConfigVO {

    private boolean enabled;

    private String authMode;

    private boolean allowLocalLogin;

    private String authorizePath;

    private String buttonText;
}
