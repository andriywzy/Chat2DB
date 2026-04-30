package ai.chat2db.server.web.start.config.sso;

import java.util.List;
import lombok.Data;

@Data
public class OidcAuthenticatedProfile {

    private String issuer;

    private String subject;

    private String userName;

    private String email;

    private String displayName;

    private List<String> groups;

    private String rawProfileSnapshot;
}
