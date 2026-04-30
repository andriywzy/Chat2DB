package ai.chat2db.server.web.start.config.sso;

public enum SsoAuthMode {
    LOCAL_ONLY,
    LOCAL_AND_OIDC,
    OIDC_ONLY;

    public boolean allowLocalLogin() {
        return this != OIDC_ONLY;
    }

    public boolean allowOidcLogin() {
        return this != LOCAL_ONLY;
    }

    public static SsoAuthMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return LOCAL_AND_OIDC;
        }
        for (SsoAuthMode value : values()) {
            if (value.name().equalsIgnoreCase(code)) {
                return value;
            }
        }
        return LOCAL_AND_OIDC;
    }
}
