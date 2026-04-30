package ai.chat2db.server.web.api.controller.ai;

import ai.chat2db.server.tools.common.exception.ParamBusinessException;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiChatCommand;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.orchestrator.AiOrchestrator;
import ai.chat2db.server.web.api.controller.ai.platform.prompt.AiSchemaContextService;
import ai.chat2db.server.web.api.controller.ai.platform.retrieval.AiRetrievalService;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * @author moji
 */
@RestController
@RequestMapping("/api/ai/text/generation")
@Slf4j
public class TextGenerationController extends ChatController {


    /**
     * chat timeout time
     */
    private static final Long CHAT_TIMEOUT = Duration.ofMinutes(50).toMillis();

    @Resource
    private AiSchemaContextService aiSchemaContextService;

    @Resource
    private AiRetrievalService aiRetrievalService;

    @Resource
    private AiOrchestrator aiOrchestrator;

    /**
     * sql auto complete
     *
     * @param queryRequest
     * @return
     * @throws IOException
     */
    @GetMapping("/prompt")
    @CrossOrigin
    public SseEmitter prompt(ChatQueryRequest queryRequest, @RequestHeader Map<String, String> headers)
            throws Exception {
        queryRequest.setPromptType(PromptType.TEXT_GENERATION.getCode());

        String promptTemplate = "### Instructions:\n" +
                "Your task is generate a SQL query according to the prompt %s.\n" +
                "Adhere to these rules:\n" +
                "- **Deliberately go through the prompt and database schema word by word** to appropriately answer the question\n" +
                "- **Use Table Aliases** to prevent ambiguity. For example, `SELECT table1.col1, table2.col1 FROM table1 JOIN table2 ON table1.id = table2.id`.\n" +
                "\n" +
                "### Input:\n" +
                "Generate a SQL query according to the prompt `%s`.\n" +
                "%s\n" +
                "\n" +
                "### Response:\n" +
                "Based on your instructions, here is the SQL query I have generated to complete the prompt `{%s}`:\n" +
                "```sql";

        // query database schema info
        String databaseType = aiSchemaContextService.queryDatabaseType(queryRequest);
        AiRetrievalContext retrievalContext = aiRetrievalService.retrieveSchema(AiRetrievalQuery.builder()
            .dataSourceId(queryRequest.getDataSourceId())
            .databaseName(queryRequest.getDatabaseName())
            .schemaName(queryRequest.getSchemaName())
            .message(queryRequest.getMessage())
            .build());
        String schemas = retrievalContext == null || retrievalContext.getSchemaSnippets() == null
            ? ""
            : com.alibaba.fastjson2.JSON.toJSONString(retrievalContext.getSchemaSnippets());
        if (StringUtils.isNotBlank(schemas)) {
            databaseType = String.format(", given a %s database schema", databaseType);
            schemas = String.format("This query will run on a database whose schema is represented in this string:\n$s", schemas);
        } else  {
            databaseType = "";
            schemas = "";
        }
        String prompt = String.format(promptTemplate, databaseType, queryRequest.getMessage(), schemas, queryRequest.getMessage());

        // chat with AI
        if (StringUtils.isBlank(queryRequest.getMessage())) {
            throw new ParamBusinessException("message");
        }

        return aiOrchestrator.streamChat(AiChatCommand.builder()
            .queryRequest(queryRequest)
            .uid(resolveUid(queryRequest, headers))
            .promptOverride(prompt)
            .retrievalContext(retrievalContext)
            .emitter(new SseEmitter(CHAT_TIMEOUT))
            .build());
    }

}
