package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.model.KnowledgeDocument;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentCreateParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentPageQueryParam;
import ai.chat2db.server.domain.api.param.knowledge.KnowledgeDocumentUpdateParam;
import ai.chat2db.server.domain.api.service.KnowledgeDocumentService;
import ai.chat2db.server.domain.core.util.PermissionUtils;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.KnowledgeDocumentDO;
import ai.chat2db.server.domain.repository.mapper.KnowledgeDocumentMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.common.exception.DataNotFoundException;
import ai.chat2db.server.tools.common.model.EasyLambdaQueryWrapper;
import ai.chat2db.server.tools.common.util.ContextUtils;
import ai.chat2db.server.tools.common.util.EasySqlUtils;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private KnowledgeDocumentMapper getMapper() {
        return Dbutils.getMapper(KnowledgeDocumentMapper.class);
    }

    @Override
    public DataResult<Long> create(KnowledgeDocumentCreateParam param) {
        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO();
        fillCreateFields(documentDO, param);
        documentDO.setUserId(ContextUtils.getUserId());
        LocalDateTime now = DateUtil.date().toLocalDateTime();
        documentDO.setGmtCreate(now);
        documentDO.setGmtModified(now);
        getMapper().insert(documentDO);
        return DataResult.of(documentDO.getId());
    }

    @Override
    public ActionResult update(KnowledgeDocumentUpdateParam param) {
        KnowledgeDocument existing = queryExistent(param.getId()).getData();
        PermissionUtils.checkOperationPermission(existing.getUserId());

        KnowledgeDocumentDO documentDO = new KnowledgeDocumentDO();
        documentDO.setId(param.getId());
        fillUpdateFields(documentDO, param);
        documentDO.setGmtModified(DateUtil.date().toLocalDateTime());
        getMapper().updateById(documentDO);
        return ActionResult.isSuccess();
    }

    @Override
    public DataResult<KnowledgeDocument> queryExistent(Long id) {
        KnowledgeDocumentDO documentDO = getMapper().selectById(id);
        if (documentDO == null) {
            throw new DataNotFoundException();
        }
        return DataResult.of(toModel(documentDO));
    }

    @Override
    public PageResult<KnowledgeDocument> queryPage(KnowledgeDocumentPageQueryParam param) {
        EasyLambdaQueryWrapper<KnowledgeDocumentDO> queryWrapper = new EasyLambdaQueryWrapper<>();
        queryWrapper.eqWhenPresent(KnowledgeDocumentDO::getUserId, param.getUserId())
            .eqWhenPresent(KnowledgeDocumentDO::getStatus, param.getStatus());
        if (StringUtils.isNotBlank(param.getSearchKey())) {
            queryWrapper.and(wrapper -> wrapper
                .like(KnowledgeDocumentDO::getName, EasySqlUtils.buildLikeRightFuzzy(param.getSearchKey()))
                .or()
                .like(KnowledgeDocumentDO::getFileName, EasySqlUtils.buildLikeRightFuzzy(param.getSearchKey())));
        }

        Page<KnowledgeDocumentDO> page = new Page<>(param.getPageNo(), param.getPageSize());
        page.setOptimizeCountSql(false);
        page.setOrders(Arrays.asList(OrderItem.desc("gmt_create")));
        IPage<KnowledgeDocumentDO> result = getMapper().selectPage(page, queryWrapper);
        List<KnowledgeDocument> documents = result.getRecords().stream().map(this::toModel).toList();
        if (CollectionUtils.isEmpty(documents)) {
            return PageResult.empty(param.getPageNo(), param.getPageSize());
        }
        return PageResult.of(documents, result.getTotal(), param);
    }

    @Override
    public ActionResult deleteWithPermission(Long id) {
        KnowledgeDocument document = queryExistent(id).getData();
        PermissionUtils.checkOperationPermission(document.getUserId());
        getMapper().deleteById(id);
        return ActionResult.isSuccess();
    }

    private void fillCreateFields(KnowledgeDocumentDO target, KnowledgeDocumentCreateParam source) {
        target.setName(source.getName());
        target.setFileName(source.getFileName());
        target.setFileType(source.getFileType());
        target.setStatus(source.getStatus());
        target.setSentenceCount(source.getSentenceCount());
        target.setWordCount(source.getWordCount());
        target.setVectorCount(source.getVectorCount());
        target.setContentPreview(source.getContentPreview());
        target.setErrorMessage(source.getErrorMessage());
    }

    private void fillUpdateFields(KnowledgeDocumentDO target, KnowledgeDocumentUpdateParam source) {
        target.setName(source.getName());
        target.setFileName(source.getFileName());
        target.setFileType(source.getFileType());
        target.setStatus(source.getStatus());
        target.setSentenceCount(source.getSentenceCount());
        target.setWordCount(source.getWordCount());
        target.setVectorCount(source.getVectorCount());
        target.setContentPreview(source.getContentPreview());
        target.setErrorMessage(source.getErrorMessage());
    }

    private KnowledgeDocument toModel(KnowledgeDocumentDO documentDO) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(documentDO.getId());
        document.setGmtCreate(documentDO.getGmtCreate());
        document.setGmtModified(documentDO.getGmtModified());
        document.setUserId(documentDO.getUserId());
        document.setName(documentDO.getName());
        document.setFileName(documentDO.getFileName());
        document.setFileType(documentDO.getFileType());
        document.setStatus(documentDO.getStatus());
        document.setSentenceCount(documentDO.getSentenceCount());
        document.setWordCount(documentDO.getWordCount());
        document.setVectorCount(documentDO.getVectorCount());
        document.setContentPreview(documentDO.getContentPreview());
        document.setErrorMessage(documentDO.getErrorMessage());
        return document;
    }
}
