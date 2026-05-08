package ai.chat2db.server.admin.api.controller.audit;

import ai.chat2db.server.admin.api.controller.audit.request.AuditPageQueryRequest;
import ai.chat2db.server.admin.api.controller.audit.vo.AuditVO;
import ai.chat2db.server.domain.api.model.AuditRecord;
import ai.chat2db.server.domain.api.param.audit.AuditPageQueryParam;
import ai.chat2db.server.domain.api.service.AuditService;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit")
public class AuditAdminController {

    @Resource
    private AuditService auditService;

    @GetMapping("/page")
    public WebPageResult<AuditVO> page(@Valid AuditPageQueryRequest request) {
        AuditPageQueryParam param = new AuditPageQueryParam();
        BeanUtils.copyProperties(request, param);
        PageResult<AuditRecord> pageResult = auditService.queryPage(param);
        List<AuditVO> rows = pageResult.getData().stream().map(this::toVO).toList();
        return WebPageResult.of(rows, pageResult.getTotal(), pageResult.getPageNo(), pageResult.getPageSize());
    }

    @GetMapping("/{id}")
    public DataResult<AuditVO> detail(@PathVariable String id) {
        DataResult<AuditRecord> result = auditService.queryDetail(id);
        return DataResult.of(result.getData() == null ? null : toVO(result.getData()));
    }

    private AuditVO toVO(AuditRecord record) {
        AuditVO vo = new AuditVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }
}
