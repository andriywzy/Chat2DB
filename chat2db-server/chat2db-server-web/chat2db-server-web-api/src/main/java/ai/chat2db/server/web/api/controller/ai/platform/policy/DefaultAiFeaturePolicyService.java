package ai.chat2db.server.web.api.controller.ai.platform.policy;

import ai.chat2db.server.tools.base.enums.WhiteListTypeEnum;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.request.WhiteListRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DefaultAiFeaturePolicyService implements AiFeaturePolicyService {

    private final GatewayClientService gatewayClientService;

    public DefaultAiFeaturePolicyService(GatewayClientService gatewayClientService) {
        this.gatewayClientService = gatewayClientService;
    }

    @Override
    public boolean supportsSchemaVectorSearch(String apiKey) {
        if (StringUtils.isBlank(apiKey)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                gatewayClientService.checkInWhite(new WhiteListRequest(apiKey, WhiteListTypeEnum.VECTOR.getCode())).getData()
            );
        } catch (Exception exception) {
            log.warn("Failed to check AI feature policy", exception);
            return false;
        }
    }
}
