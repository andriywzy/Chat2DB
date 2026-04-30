package ai.chat2db.gateway.start.controller;

import ai.chat2db.gateway.start.service.ProxyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProxyController {

    @Resource
    private ProxyService proxyService;

    @RequestMapping("/api/client/{*path}")
    public ResponseEntity<byte[]> proxyClient(HttpServletRequest request,
        @RequestBody(required = false) byte[] requestBody) throws Exception {
        return proxyService.proxy(request, requestBody);
    }

    @RequestMapping({"/model", "/model/{*path}"})
    public ResponseEntity<byte[]> proxyModel(HttpServletRequest request,
        @RequestBody(required = false) byte[] requestBody) throws Exception {
        return proxyService.proxy(request, requestBody);
    }
}
