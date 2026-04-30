package ai.chat2db.server.web.start.controller.sso;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.start.controller.sso.vo.OidcPublicConfigVO;
import ai.chat2db.server.web.start.service.sso.OidcAuthenticationService;
import ai.chat2db.server.web.start.service.sso.SsoConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth/oidc")
public class OidcOauthController {

    private final SsoConfigService ssoConfigService;
    private final OidcAuthenticationService oidcAuthenticationService;

    public OidcOauthController(SsoConfigService ssoConfigService, OidcAuthenticationService oidcAuthenticationService) {
        this.ssoConfigService = ssoConfigService;
        this.oidcAuthenticationService = oidcAuthenticationService;
    }

    @GetMapping("/config")
    public DataResult<OidcPublicConfigVO> config() {
        return DataResult.of(ssoConfigService.getPublicConfig());
    }

    @GetMapping("/authorize")
    public void authorize(@RequestParam(value = "callback", required = false) String callback, HttpServletResponse response)
        throws IOException {
        response.sendRedirect(oidcAuthenticationService.buildAuthorizeUrl(callback));
    }

    @GetMapping("/callback")
    public void callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state,
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException {
        response.sendRedirect(oidcAuthenticationService.handleCallback(code, state, request));
    }
}
