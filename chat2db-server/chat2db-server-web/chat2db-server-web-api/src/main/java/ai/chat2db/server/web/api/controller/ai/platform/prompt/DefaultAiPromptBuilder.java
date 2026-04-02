package ai.chat2db.server.web.api.controller.ai.platform.prompt;

import ai.chat2db.server.tools.common.util.EasyEnumUtils;
import ai.chat2db.server.web.api.controller.ai.enums.PromptType;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.retrieval.AiRetrievalService;
import ai.chat2db.server.web.api.controller.ai.request.ChatQueryRequest;
import ai.chat2db.server.web.api.http.model.Knowledge;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAiPromptBuilder implements AiPromptBuilder {

    private final AiSchemaContextService schemaContextService;
    private final AiRetrievalService retrievalService;

    public DefaultAiPromptBuilder(AiSchemaContextService schemaContextService, AiRetrievalService retrievalService) {
        this.schemaContextService = schemaContextService;
        this.retrievalService = retrievalService;
    }

    @Override
    public String buildPrompt(ChatQueryRequest queryRequest) {
        return buildPrompt(queryRequest, null);
    }

    @Override
    public String buildPrompt(ChatQueryRequest queryRequest, AiRetrievalContext retrievalContext) {
        if (PromptType.TEXT_GENERATION.getCode().equals(queryRequest.getPromptType())) {
            return queryRequest.getMessage();
        }

        String dataSourceType = schemaContextService.queryDatabaseType(queryRequest);
        List<String> properties;
        if (CollectionUtils.isNotEmpty(queryRequest.getTableNames())) {
            properties = schemaContextService.buildSelectedTableSchemas(queryRequest);
        } else {
            AiRetrievalContext context = retrievalContext;
            if (context == null) {
                context = retrievalService.retrieveSchema(AiRetrievalQuery.builder()
                    .dataSourceId(queryRequest.getDataSourceId())
                    .databaseName(queryRequest.getDatabaseName())
                    .schemaName(queryRequest.getSchemaName())
                    .message(queryRequest.getMessage())
                    .build());
            }
            properties = context == null ? List.of() : context.getSchemaSnippets();
        }

        String prompt = queryRequest.getMessage();
        String promptType = StringUtils.isBlank(queryRequest.getPromptType()) ? PromptType.NL_2_SQL.getCode()
            : queryRequest.getPromptType();
        PromptType pType = EasyEnumUtils.getEnum(PromptType.class, promptType);
        String ext = StringUtils.defaultString(queryRequest.getExt());
        String schemaProperty = CollectionUtils.isNotEmpty(properties)
            ? String.format(
                "### Please follow the below table properties and SQL input%s. %s\n#\n### %s SQL tables, with their properties:\n#\n# %s\n#\n#\n### SQL input: %s",
                pType.getDescription(), ext, dataSourceType, JSON.toJSONString(properties), prompt)
            : String.format("### Please follow the below SQL input%s. %s\n#\n### SQL input: %s",
                pType.getDescription(), ext, prompt);
        if (PromptType.SQL_2_SQL.equals(pType)) {
            schemaProperty = StringUtils.isNotBlank(queryRequest.getDestSqlType())
                ? String.format("%s\n#\n### Target SQL type: %s", schemaProperty, queryRequest.getDestSqlType())
                : String.format("%s\n#\n### Target SQL type: %s", schemaProperty, dataSourceType);
        }
        return schemaProperty.replaceAll("[\r\t]", "");
    }

    @Override
    public String buildKnowledgePrompt(String question, AiRetrievalContext retrievalContext) {
        List<Knowledge> knowledgeSources = retrievalContext == null ? List.of() : retrievalContext.getKnowledgeSources();
        if (CollectionUtils.isEmpty(knowledgeSources)) {
            return String.format(
                "你正在回答一个知识库问题，但当前没有检索到可用知识片段。"
                    + "你必须直接回复：未在知识库中找到答案。\n\n"
                    + "用户问题：\n%s",
                question
            );
        }

        String serializedContents = formatKnowledgeSources(knowledgeSources);
        return String.format(
            "你正在基于知识库检索结果回答问题。下面的片段已经按相关性从高到低排序，后面的片段可能是噪声、OCR 残片或次要信息。"
                + "你必须严格遵守以下规则：\n"
                + "1. 只能依据下面的知识片段回答。\n"
                + "2. 优先使用排名靠前、与问题最直接相关的片段；忽略不相关或明显噪声片段。\n"
                + "3. 不要把多个互不相关的片段拼接成一个答案。\n"
                + "4. 如果片段不足以回答，必须且只能回复：未在知识库中找到答案。\n"
                + "5. 不要说无法访问文件、无法访问外部系统、无法查看上传文档之类的话。\n"
                + "6. 回答保持简洁，并使用与用户问题相同的语言。\n"
                + "7. 如果用户要求原文、原句、精确文本，直接返回最相关片段中的原文，不要额外解释。\n\n"
                + "知识片段（已排序，含来源）：\n%s\n\n"
                + "用户问题：\n%s",
            serializedContents,
            question
        );
    }

    private String formatKnowledgeSources(List<Knowledge> knowledgeSources) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < knowledgeSources.size(); index++) {
            Knowledge knowledge = knowledgeSources.get(index);
            builder.append(index + 1)
                .append(". [文档: ")
                .append(StringUtils.defaultIfBlank(knowledge.getDocumentName(), "未知文档"));
            if (StringUtils.isNotBlank(knowledge.getFileType())) {
                builder.append(" | 类型: ").append(knowledge.getFileType().toUpperCase());
            }
            builder.append("]");
            if (knowledge.getScore() != null) {
                builder.append(" [相关性: ").append(String.format("%.2f", knowledge.getScore())).append("]");
            }
            builder.append("\n")
                .append(knowledge.getContent())
                .append("\n");
        }
        return builder.toString().trim();
    }
}
