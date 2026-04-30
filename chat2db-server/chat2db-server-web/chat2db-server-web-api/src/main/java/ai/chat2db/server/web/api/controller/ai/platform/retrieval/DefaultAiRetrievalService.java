package ai.chat2db.server.web.api.controller.ai.platform.retrieval;

import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.domain.api.enums.KnowledgeDocumentStatusEnum;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.web.api.controller.ai.platform.config.AiConfigResolver;
import ai.chat2db.server.web.api.controller.ai.platform.embedding.AiEmbeddingService;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiEmbeddingResponse;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalContext;
import ai.chat2db.server.web.api.controller.ai.platform.model.AiRetrievalQuery;
import ai.chat2db.server.web.api.controller.ai.platform.policy.AiFeaturePolicyService;
import ai.chat2db.server.web.api.http.GatewayClientService;
import ai.chat2db.server.web.api.http.model.EsTableSchema;
import ai.chat2db.server.web.api.http.model.Knowledge;
import ai.chat2db.server.web.api.http.model.TableSchema;
import ai.chat2db.server.web.api.http.request.EsTableSchemaRequest;
import ai.chat2db.server.web.api.http.request.KnowledgeRequest;
import ai.chat2db.server.web.api.http.request.TableSchemaRequest;
import ai.chat2db.server.web.api.http.response.EsTableSchemaResponse;
import ai.chat2db.server.web.api.http.response.KnowledgeResponse;
import ai.chat2db.server.web.api.http.response.TableSchemaResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DefaultAiRetrievalService implements AiRetrievalService {

    private static final int MAX_KNOWLEDGE_SNIPPETS = 4;
    private static final int MAX_SNIPPET_LENGTH = 320;
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private static final Pattern CJK_TOKEN_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]{2,}");

    private final AiEmbeddingService aiEmbeddingService;
    private final AiConfigResolver aiConfigResolver;
    private final AiFeaturePolicyService featurePolicyService;
    private final GatewayClientService gatewayClientService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final ProjectSchemaContextService projectSchemaContextService;

    public DefaultAiRetrievalService(
        AiEmbeddingService aiEmbeddingService,
        AiConfigResolver aiConfigResolver,
        AiFeaturePolicyService featurePolicyService,
        GatewayClientService gatewayClientService,
        KnowledgeDocumentService knowledgeDocumentService,
        ProjectSchemaContextService projectSchemaContextService
    ) {
        this.aiEmbeddingService = aiEmbeddingService;
        this.aiConfigResolver = aiConfigResolver;
        this.featurePolicyService = featurePolicyService;
        this.gatewayClientService = gatewayClientService;
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.projectSchemaContextService = projectSchemaContextService;
    }

    @Override
    public AiRetrievalContext retrieveSchema(AiRetrievalQuery query) {
        if (query == null || query.getDataSourceId() == null || StringUtils.isBlank(query.getMessage())) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }
        AiRetrievalContext projectScopedContext = projectSchemaContextService.retrieveSchema(query);
        if (projectScopedContext != null && projectScopedContext.hasSchemaSnippets()) {
            return projectScopedContext;
        }
        String apiKey = aiConfigResolver.getChat2dbApiKey();
        if (!featurePolicyService.supportsSchemaVectorSearch(apiKey)) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }
        AiEmbeddingResponse embeddingResponse = aiEmbeddingService.embed(query.getMessage());
        if (embeddingResponse == null || CollectionUtils.isEmpty(embeddingResponse.getVectors())) {
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }
        try {
            TableSchemaRequest request = new TableSchemaRequest();
            request.setSchemaVector(embeddingResponse.getVectors());
            request.setDataSourceId(query.getDataSourceId());
            request.setDatabaseName(query.getDatabaseName());
            request.setDataSourceSchema(query.getSchemaName());
            request.setApiKey(apiKey);
            DataResult<TableSchemaResponse> result = gatewayClientService.schemaVectorSearch(request);
            List<String> schemas = new ArrayList<>();
            if (Objects.nonNull(result.getData()) && CollectionUtils.isNotEmpty(result.getData().getTableSchemas())) {
                for (TableSchema data : result.getData().getTableSchemas()) {
                    schemas.add(data.getTableSchema());
                }
            }
            return AiRetrievalContext.builder().schemaSnippets(schemas).schemaSources(List.of()).build();
        } catch (Exception exception) {
            log.warn("Failed to retrieve schema context", exception);
            return AiRetrievalContext.builder().schemaSnippets(List.of()).schemaSources(List.of()).build();
        }
    }

    @Override
    public AiRetrievalContext retrieveKnowledge(AiRetrievalQuery query) {
        if (query == null || StringUtils.isBlank(query.getMessage())) {
            return emptyKnowledgeContext();
        }
        List<Long> readyDocumentIds = resolveReadyDocumentIds(query);
        AiEmbeddingResponse embeddingResponse = aiEmbeddingService.embed(query.getMessage());
        if (embeddingResponse == null || CollectionUtils.isEmpty(embeddingResponse.getVectors())) {
            return emptyKnowledgeContext();
        }
        try {
            KnowledgeRequest knowledgeRequest = new KnowledgeRequest();
            knowledgeRequest.setUserId(query.getUserId());
            knowledgeRequest.setDocumentIds(readyDocumentIds);
            knowledgeRequest.setContentVector(embeddingResponse.getVectors());
            DataResult<KnowledgeResponse> result = gatewayClientService.knowledgeVectorSearch(knowledgeRequest);
            List<RankedKnowledgeSnippet> rankedSnippets = new ArrayList<>();
            List<Knowledge> rawSources = new ArrayList<>();
            if (result != null && result.getData() != null && CollectionUtils.isNotEmpty(result.getData().getKnowledgeList())) {
                int index = 0;
                for (Knowledge data : result.getData().getKnowledgeList()) {
                    String sanitizedContent = sanitizeSnippet(data.getContent());
                    if (StringUtils.isNotBlank(sanitizedContent)) {
                        Knowledge source = new Knowledge();
                        source.setId(data.getId());
                        source.setDocumentId(data.getDocumentId());
                        source.setDocumentName(data.getDocumentName());
                        source.setFileType(data.getFileType());
                        source.setWordCount(data.getWordCount());
                        source.setScore(data.getScore());
                        source.setContent(truncateSnippet(sanitizedContent));
                        rawSources.add(source);
                        rankedSnippets.add(new RankedKnowledgeSnippet(
                            source,
                            scoreKnowledgeSnippet(query.getMessage(), sanitizedContent, index)
                        ));
                    }
                    index++;
                }
            }
            enrichKnowledgeSources(rawSources);
            List<Knowledge> selectedSources = selectKnowledgeSources(rankedSnippets, query.getLimit());
            return AiRetrievalContext.builder()
                .knowledgeSnippets(selectedSources.stream().map(Knowledge::getContent).toList())
                .knowledgeSources(selectedSources)
                .build();
        } catch (Exception exception) {
            log.warn("Failed to retrieve knowledge context", exception);
            return emptyKnowledgeContext();
        }
    }

    private AiRetrievalContext emptyKnowledgeContext() {
        return AiRetrievalContext.builder()
            .knowledgeSnippets(List.of())
            .knowledgeSources(List.of())
            .build();
    }

    private List<Long> resolveReadyDocumentIds(AiRetrievalQuery query) {
        if (CollectionUtils.isEmpty(query.getDocumentIds())) {
            return List.of();
        }
        List<KnowledgeDocument> documents = knowledgeDocumentService.queryByIds(query.getDocumentIds());
        List<Long> readyDocumentIds = documents.stream()
            .filter(document -> Objects.equals(document.getUserId(), query.getUserId()))
            .filter(document -> KnowledgeDocumentStatusEnum.READY.name().equals(document.getStatus()))
            .map(KnowledgeDocument::getId)
            .toList();
        if (CollectionUtils.isEmpty(readyDocumentIds)) {
            throw new BusinessException("knowledge.search.document.not.ready");
        }
        return readyDocumentIds;
    }

    private void enrichKnowledgeSources(List<Knowledge> knowledgeSources) {
        List<Long> documentIds = knowledgeSources.stream()
            .map(Knowledge::getDocumentId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (CollectionUtils.isEmpty(documentIds)) {
            return;
        }
        Map<Long, KnowledgeDocument> documentMap = new HashMap<>();
        for (KnowledgeDocument document : knowledgeDocumentService.queryByIds(documentIds)) {
            documentMap.put(document.getId(), document);
        }
        for (Knowledge source : knowledgeSources) {
            KnowledgeDocument document = documentMap.get(source.getDocumentId());
            if (document == null) {
                continue;
            }
            if (StringUtils.isBlank(source.getDocumentName())) {
                source.setDocumentName(document.getName());
            }
            if (StringUtils.isBlank(source.getFileType())) {
                source.setFileType(document.getFileType());
            }
        }
    }

    private List<Knowledge> selectKnowledgeSources(List<RankedKnowledgeSnippet> rankedSnippets, Integer requestedLimit) {
        if (CollectionUtils.isEmpty(rankedSnippets)) {
            return List.of();
        }
        int maxSnippetCount = requestedLimit == null || requestedLimit <= 0
            ? MAX_KNOWLEDGE_SNIPPETS
            : Math.min(requestedLimit, MAX_KNOWLEDGE_SNIPPETS);
        rankedSnippets.sort(Comparator.comparingDouble(RankedKnowledgeSnippet::score).reversed());
        List<Knowledge> selected = new ArrayList<>();
        Set<String> normalizedSelected = new LinkedHashSet<>();
        for (RankedKnowledgeSnippet snippet : rankedSnippets) {
            String normalized = normalizeForMatch(snippet.source().getContent());
            if (StringUtils.isBlank(normalized)) {
                continue;
            }
            boolean duplicated = normalizedSelected.stream()
                .anyMatch(existing -> existing.contains(normalized) || normalized.contains(existing));
            if (duplicated) {
                continue;
            }
            normalizedSelected.add(normalized);
            Knowledge selectedSource = snippet.source();
            selectedSource.setScore((float) snippet.score());
            selected.add(selectedSource);
            if (selected.size() >= maxSnippetCount) {
                break;
            }
        }
        return selected;
    }

    private double scoreKnowledgeSnippet(String question, String snippet, int originalIndex) {
        String normalizedQuestion = normalizeForMatch(question);
        String normalizedSnippet = normalizeForMatch(snippet);
        if (StringUtils.isBlank(normalizedSnippet)) {
            return Double.NEGATIVE_INFINITY;
        }

        Set<String> keywords = extractKeywords(question);
        int matchedKeywords = 0;
        for (String keyword : keywords) {
            if (normalizedSnippet.contains(normalizeForMatch(keyword))) {
                matchedKeywords++;
            }
        }

        double score = Math.max(0, 6 - originalIndex) * 3.0;
        if (StringUtils.isNotBlank(normalizedQuestion) && normalizedSnippet.contains(normalizedQuestion)) {
            score += 18;
        }
        if (!keywords.isEmpty()) {
            score += ((double) matchedKeywords / keywords.size()) * 20;
            score += matchedKeywords * 2;
        }
        int snippetLength = snippet.length();
        if (snippetLength <= 120) {
            score += 4;
        } else if (snippetLength > 240) {
            score -= (snippetLength - 240) / 40.0;
        }
        score -= estimateNoisePenalty(snippet);
        return score;
    }

    private Set<String> extractKeywords(String question) {
        Set<String> keywords = new LinkedHashSet<>();
        if (StringUtils.isBlank(question)) {
            return keywords;
        }
        String lowerQuestion = question.toLowerCase();
        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(lowerQuestion);
        while (englishMatcher.find()) {
            keywords.add(englishMatcher.group());
        }
        Matcher cjkMatcher = CJK_TOKEN_PATTERN.matcher(question);
        while (cjkMatcher.find()) {
            String token = cjkMatcher.group();
            if (token.length() <= 8) {
                keywords.add(token);
            } else {
                keywords.add(token.substring(0, 8));
            }
        }
        return keywords;
    }

    private String sanitizeSnippet(String content) {
        if (StringUtils.isBlank(content)) {
            return StringUtils.EMPTY;
        }
        return content
            .replace('\u0000', ' ')
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private String truncateSnippet(String content) {
        if (content.length() <= MAX_SNIPPET_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }

    private String normalizeForMatch(String content) {
        if (StringUtils.isBlank(content)) {
            return StringUtils.EMPTY;
        }
        return content.toLowerCase()
            .replaceAll("\\s+", "")
            .replaceAll("[^a-z0-9\\u4e00-\\u9fff]", "");
    }

    private double estimateNoisePenalty(String snippet) {
        if (StringUtils.isBlank(snippet)) {
            return 0;
        }
        int suspiciousCount = 0;
        for (char ch : snippet.toCharArray()) {
            boolean asciiLetterOrDigit = Character.isLetterOrDigit(ch);
            boolean cjk = ch >= '\u4e00' && ch <= '\u9fff';
            boolean commonPunctuation = Character.isWhitespace(ch) || ".,!?;:，。！？；：（）()[]【】《》、“”‘’'\"-_/|".indexOf(ch) >= 0;
            if (!asciiLetterOrDigit && !cjk && !commonPunctuation) {
                suspiciousCount++;
            }
        }
        return ((double) suspiciousCount / Math.max(1, snippet.length())) * 12;
    }

    private record RankedKnowledgeSnippet(Knowledge source, double score) {
    }
}
