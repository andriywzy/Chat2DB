package ai.chat2db.server.web.start.controller.sso;

import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import ai.chat2db.server.web.start.controller.sso.request.SsoAdminConfigRequest;
import ai.chat2db.server.web.start.controller.sso.request.SsoGroupMappingRequest;
import ai.chat2db.server.web.start.controller.sso.vo.SsoAdminConfigVO;
import ai.chat2db.server.web.start.controller.sso.vo.SsoGroupMappingVO;
import ai.chat2db.server.web.start.service.sso.SsoConfigService;
import ai.chat2db.server.web.start.service.sso.SsoGroupMappingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sso")
public class SsoAdminController {

    private final SsoConfigService ssoConfigService;
    private final SsoGroupMappingService ssoGroupMappingService;

    public SsoAdminController(SsoConfigService ssoConfigService, SsoGroupMappingService ssoGroupMappingService) {
        this.ssoConfigService = ssoConfigService;
        this.ssoGroupMappingService = ssoGroupMappingService;
    }

    @GetMapping("/config")
    public DataResult<SsoAdminConfigVO> config() {
        return DataResult.of(ssoConfigService.getAdminConfig());
    }

    @PostMapping("/config")
    public ActionResult updateConfig(@RequestBody SsoAdminConfigRequest request) {
        return ssoConfigService.saveAdminConfig(request);
    }

    @PostMapping("/test-connection")
    public DataResult<Object> testConnection(@RequestBody(required = false) SsoAdminConfigRequest request) {
        return DataResult.of(ssoConfigService.testConnection(request));
    }

    @GetMapping("/group-mapping/page")
    public WebPageResult<SsoGroupMappingVO> page(Integer pageNo, Integer pageSize, String searchKey) {
        return ssoGroupMappingService.page(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize, searchKey);
    }

    @PostMapping("/group-mapping/create")
    public ActionResult create(@Valid @RequestBody SsoGroupMappingRequest request) {
        return ssoGroupMappingService.create(request);
    }

    @PostMapping("/group-mapping/update")
    public ActionResult update(@Valid @RequestBody SsoGroupMappingRequest request) {
        return ssoGroupMappingService.update(request);
    }

    @DeleteMapping("/group-mapping/{id}")
    public ActionResult delete(@PathVariable Long id) {
        return ssoGroupMappingService.delete(id);
    }
}
