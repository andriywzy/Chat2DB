package ai.chat2db.gateway.start.controller;

import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public DataResult<Map<String, String>> health() {
        return DataResult.of(Map.of("status", "UP"));
    }
}
