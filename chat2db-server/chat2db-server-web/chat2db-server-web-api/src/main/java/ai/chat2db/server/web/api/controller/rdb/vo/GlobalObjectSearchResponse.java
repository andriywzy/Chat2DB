package ai.chat2db.server.web.api.controller.rdb.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GlobalObjectSearchResponse {

    private List<GlobalObjectSearchItemVO> data;

    private Long total;

    private Boolean partial;

    private List<String> warnings;

    private Map<String, Long> countsByType;
}
