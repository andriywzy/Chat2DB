package ai.chat2db.server.admin.api.controller.user;

import ai.chat2db.server.admin.api.controller.user.converter.UserAdminConverter;
import ai.chat2db.server.admin.api.controller.user.request.UserCreateRequest;
import ai.chat2db.server.admin.api.controller.user.request.UserUpdateRequest;
import ai.chat2db.server.admin.api.controller.user.vo.UserPageQueryVO;
import ai.chat2db.server.common.api.controller.request.CommonPageQueryRequest;
import ai.chat2db.server.domain.api.param.team.TeamPageQueryParam.OrderCondition;
import ai.chat2db.server.domain.api.param.user.UserPageQueryParam;
import ai.chat2db.server.domain.api.param.user.UserSelector;
import ai.chat2db.server.domain.api.service.UserService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.UserIdentityBindingDO;
import ai.chat2db.server.domain.repository.mapper.UserIdentityBindingMapper;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.tools.base.wrapper.result.web.WebPageResult;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Management
 *
 * @author Jiaju Zhuang
 */
@RequestMapping("/api/admin/user")
@RestController
public class UserAdminController {

    private static final UserSelector USER_SELECTOR = UserSelector.builder()
        .modifiedUser(Boolean.TRUE)
        .build();

    @Resource
    private UserService userService;
    @Resource
    private UserAdminConverter userAdminConverter;

    /**
     * Pagination query
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @GetMapping("/page")
    public WebPageResult<UserPageQueryVO> page(@Valid CommonPageQueryRequest request) {
        UserPageQueryParam param = userAdminConverter.request2param(request);
        param.orderBy(OrderCondition.ID_DESC);
        WebPageResult<UserPageQueryVO> result = userService.pageQuery(param, USER_SELECTOR)
            .mapToWeb(userAdminConverter::dto2vo);
        enrichSsoBinding(result);
        return result;
    }

    /**
     * create
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @PostMapping("/create")
    public DataResult<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(userAdminConverter.request2param(request));
    }

    /**
     * update
     *
     * @param request
     * @return
     * @version 2.1.0
     */
    @PostMapping("/update")
    public DataResult<Long> update(@RequestBody UserUpdateRequest request) {
        return userService.update(userAdminConverter.request2param(request));
    }

    /**
     * delete
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    public DataResult<Boolean> delete(@PathVariable Long id) {
        return userService.delete(id).toBooleaSuccessnDataResult();
    }

    private void enrichSsoBinding(WebPageResult<UserPageQueryVO> result) {
        if (!WebPageResult.hasData(result)) {
            return;
        }
        List<UserPageQueryVO> rows = result.getData().getData();
        List<Long> userIds = rows.stream().map(UserPageQueryVO::getId).filter(Objects::nonNull).toList();
        if (userIds.isEmpty()) {
            return;
        }

        List<UserIdentityBindingDO> bindings = Dbutils.getMapper(UserIdentityBindingMapper.class).selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserIdentityBindingDO>()
                .in(UserIdentityBindingDO::getUserId, userIds)
                .eq(UserIdentityBindingDO::getProviderType, "OIDC")
        );
        if (bindings.isEmpty()) {
            return;
        }

        Map<Long, UserIdentityBindingDO> bindingMap = new LinkedHashMap<>();
        for (UserIdentityBindingDO binding : bindings) {
            bindingMap.putIfAbsent(binding.getUserId(), binding);
        }
        for (UserPageQueryVO row : rows) {
            UserIdentityBindingDO binding = bindingMap.get(row.getId());
            if (binding == null) {
                row.setOidcBound(Boolean.FALSE);
                row.setAuthSource("LOCAL");
                continue;
            }
            row.setOidcBound(Boolean.TRUE);
            row.setAuthSource("OIDC");
            row.setOidcIssuer(binding.getIssuer());
            row.setSsoStatus(binding.getStatus());
            if (binding.getLastLoginAt() != null) {
                row.setLastSsoLoginAt(java.util.Date.from(binding.getLastLoginAt().atZone(ZoneId.systemDefault()).toInstant()));
            }
        }
    }
}
