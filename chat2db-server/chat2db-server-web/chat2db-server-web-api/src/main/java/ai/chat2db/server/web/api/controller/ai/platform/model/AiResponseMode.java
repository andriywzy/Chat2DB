package ai.chat2db.server.web.api.controller.ai.platform.model;

import org.apache.commons.lang3.StringUtils;

public enum AiResponseMode {

    SQL_ONLY,
    RICH_TEXT;

    public static AiResponseMode fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return RICH_TEXT;
        }
        for (AiResponseMode value : values()) {
            if (StringUtils.equalsIgnoreCase(value.name(), code)) {
                return value;
            }
        }
        return RICH_TEXT;
    }
}
