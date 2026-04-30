package ai.chat2db.server.web.api.controller.ai.request;

import java.util.List;

import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiResponseMode;
import ai.chat2db.server.web.api.controller.data.source.request.DataSourceBaseRequest;

import lombok.Data;

/**
 * Chat query input parameters
 *
 * @author moji
 * @version ChatQueryRequest.java, v 0.1 April 2, 2023 13:28 moji Exp $
 * @date 2023/04/02
 */
@Data
public class ChatQueryRequest extends DataSourceBaseRequest {

    /**
     * Conversation identifier.
     * Supports query-param transport so AI endpoints do not need to rely on a custom request header.
     */
    private String uid;

    /**
     * Enter message
     */
    private String message;

    /**
     * SQL function type
     * @see PromptType
     */
    private String promptType;

    /**
     * table name list
     */
    private List<String> tableNames;

    /**
     * Target SQL data type
     * @see ai.chat2db.server.domain.support.enums.DbTypeEnum
     */
    private String destSqlType;

    /**
     * More remarks: such as requirements or restrictions, etc.
     */
    private String ext;

    /**
     * Response mode.
     * SQL_ONLY: only emit executable SQL
     * RICH_TEXT: default rich text response
     */
    private String responseMode;

    public boolean isSqlOnlyMode() {
        return resolveResponseMode() == AiResponseMode.SQL_ONLY;
    }

    public AiResponseMode resolveResponseMode() {
        return AiResponseMode.fromCode(responseMode);
    }
}
