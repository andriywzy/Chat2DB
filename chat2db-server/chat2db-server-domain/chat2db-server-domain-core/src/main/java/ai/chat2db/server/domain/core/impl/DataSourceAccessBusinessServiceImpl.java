package ai.chat2db.server.domain.core.impl;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.service.DataSourceAccessBusinessService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.mapper.DataSourceCustomMapper;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.common.exception.PermissionDeniedBusinessException;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Data Source Access
 *
 * @author Jiaju Zhuang
 */
@Slf4j
@Service
public class DataSourceAccessBusinessServiceImpl implements DataSourceAccessBusinessService {

    private DataSourceCustomMapper getMapper() {
        return Dbutils.getMapper(DataSourceCustomMapper.class);
    }

    @Override
    public ActionResult checkPermission(@NotNull DataSource dataSource) {
        LoginUser loginUser = ContextUtils.getLoginUser();

        // Administrators can edit anything
        if (loginUser.getAdmin()) {
            return ActionResult.isSuccess();
        }

        Integer count = getMapper().countReadable(loginUser.getAdmin(), loginUser.getId(), dataSource.getId());
        if (count != null && count > 0) {
            return ActionResult.isSuccess();
        }

        throw new PermissionDeniedBusinessException();
    }
}
