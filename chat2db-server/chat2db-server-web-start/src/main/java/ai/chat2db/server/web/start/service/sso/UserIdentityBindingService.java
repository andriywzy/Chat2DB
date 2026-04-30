package ai.chat2db.server.web.start.service.sso;

import ai.chat2db.server.domain.core.cache.CacheKey;
import ai.chat2db.server.domain.core.cache.MemoryCacheManage;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.domain.repository.entity.DbhubUserDO;
import ai.chat2db.server.domain.repository.entity.UserIdentityBindingDO;
import ai.chat2db.server.domain.repository.mapper.DbhubUserMapper;
import ai.chat2db.server.domain.repository.mapper.UserIdentityBindingMapper;
import ai.chat2db.server.tools.base.excption.BusinessException;
import ai.chat2db.server.web.start.config.sso.OidcAuthenticatedProfile;
import ai.chat2db.server.web.start.config.sso.SsoConstants;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class UserIdentityBindingService {

    private UserIdentityBindingMapper getBindingMapper() {
        return Dbutils.getMapper(UserIdentityBindingMapper.class);
    }

    private DbhubUserMapper getUserMapper() {
        return Dbutils.getMapper(DbhubUserMapper.class);
    }

    public DbhubUserDO syncLoginUser(OidcAuthenticatedProfile profile) {
        UserIdentityBindingDO binding = queryBinding(profile.getIssuer(), profile.getSubject());
        if (binding != null) {
            DbhubUserDO user = getUserMapper().selectById(binding.getUserId());
            if (user == null) {
                throw new BusinessException("sso.bindingUserMissing");
            }
            if (isDisabled(profile)) {
                disableUser(user, binding, profile);
                throw new BusinessException("oauth.invalidUserName");
            }
            updateExistingUser(user, profile);
            updateBinding(binding, user.getId(), profile, SsoConstants.BINDING_STATUS_ACTIVE);
            return user;
        }

        DbhubUserDO matchedUser = findUniqueExistingUser(profile);
        if (matchedUser == null) {
            matchedUser = createUser(profile);
        } else if (isDisabled(profile)) {
            matchedUser.setStatus("INVALID");
            matchedUser.setGmtModified(nowDate());
            getUserMapper().updateById(matchedUser);
            MemoryCacheManage.remove(CacheKey.getLoginUserKey(matchedUser.getId()));
            throw new BusinessException("oauth.invalidUserName");
        } else {
            updateExistingUser(matchedUser, profile);
        }

        createBinding(matchedUser.getId(), profile);
        return matchedUser;
    }

    public UserIdentityBindingDO queryBinding(String issuer, String subject) {
        LambdaQueryWrapper<UserIdentityBindingDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserIdentityBindingDO::getProviderType, SsoConstants.PROVIDER_TYPE_OIDC)
            .eq(UserIdentityBindingDO::getIssuer, issuer)
            .eq(UserIdentityBindingDO::getSubjectValue, subject)
            .last("limit 1");
        return getBindingMapper().selectOne(queryWrapper);
    }

    private DbhubUserDO findUniqueExistingUser(OidcAuthenticatedProfile profile) {
        if (StringUtils.isBlank(profile.getEmail()) && StringUtils.isBlank(profile.getUserName())) {
            return null;
        }
        LambdaQueryWrapper<DbhubUserDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wrapper -> {
            boolean added = false;
            if (StringUtils.isNotBlank(profile.getEmail())) {
                wrapper.eq(DbhubUserDO::getEmail, profile.getEmail());
                added = true;
            }
            if (StringUtils.isNotBlank(profile.getUserName())) {
                if (added) {
                    wrapper.or();
                }
                wrapper.eq(DbhubUserDO::getUserName, profile.getUserName());
            }
        });
        List<DbhubUserDO> candidates = getUserMapper().selectList(queryWrapper);
        if (CollectionUtils.isEmpty(candidates)) {
            return null;
        }
        if (candidates.size() > 1) {
            throw new BusinessException("sso.accountBindingConflict");
        }
        return candidates.get(0);
    }

    private DbhubUserDO createUser(OidcAuthenticatedProfile profile) {
        DbhubUserDO user = new DbhubUserDO();
        user.setUserName(defaultUserName(profile));
        user.setNickName(defaultNickName(profile));
        user.setEmail(StringUtils.trimToNull(profile.getEmail()));
        user.setPassword(DigestUtil.bcrypt(UUID.randomUUID().toString()));
        user.setRoleCode("USER");
        user.setStatus("VALID");
        user.setCreateUserId(2L);
        user.setModifiedUserId(2L);
        user.setGmtCreate(nowDate());
        user.setGmtModified(nowDate());
        getUserMapper().insert(user);
        MemoryCacheManage.remove(CacheKey.getLoginUserKey(user.getId()));
        return user;
    }

    private void createBinding(Long userId, OidcAuthenticatedProfile profile) {
        UserIdentityBindingDO binding = new UserIdentityBindingDO();
        binding.setUserId(userId);
        binding.setProviderType(SsoConstants.PROVIDER_TYPE_OIDC);
        binding.setIssuer(profile.getIssuer());
        binding.setSubjectValue(profile.getSubject());
        binding.setUsernameClaimValue(StringUtils.trimToNull(profile.getUserName()));
        binding.setEmailClaimValue(StringUtils.trimToNull(profile.getEmail()));
        binding.setStatus(SsoConstants.BINDING_STATUS_ACTIVE);
        binding.setLastLoginAt(LocalDateTime.now());
        binding.setLastSyncAt(LocalDateTime.now());
        binding.setRawProfileSnapshot(profile.getRawProfileSnapshot());
        binding.setGmtCreate(LocalDateTime.now());
        binding.setGmtModified(LocalDateTime.now());
        getBindingMapper().insert(binding);
    }

    private void updateBinding(UserIdentityBindingDO binding, Long userId, OidcAuthenticatedProfile profile, String status) {
        binding.setUserId(userId);
        binding.setUsernameClaimValue(StringUtils.trimToNull(profile.getUserName()));
        binding.setEmailClaimValue(StringUtils.trimToNull(profile.getEmail()));
        binding.setStatus(status);
        binding.setLastLoginAt(LocalDateTime.now());
        binding.setLastSyncAt(LocalDateTime.now());
        binding.setRawProfileSnapshot(profile.getRawProfileSnapshot());
        binding.setGmtModified(LocalDateTime.now());
        getBindingMapper().updateById(binding);
    }

    private void updateExistingUser(DbhubUserDO user, OidcAuthenticatedProfile profile) {
        boolean changed = false;
        if (StringUtils.isNotBlank(profile.getEmail()) && !StringUtils.equals(profile.getEmail(), user.getEmail())) {
            user.setEmail(profile.getEmail());
            changed = true;
        }
        String nextNickName = defaultNickName(profile);
        if (StringUtils.isNotBlank(nextNickName) && !StringUtils.equals(nextNickName, user.getNickName())) {
            user.setNickName(nextNickName);
            changed = true;
        }
        if (StringUtils.isBlank(user.getUserName()) && StringUtils.isNotBlank(profile.getUserName())) {
            user.setUserName(profile.getUserName());
            changed = true;
        }
        if (Objects.equals(user.getStatus(), "INVALID")) {
            user.setStatus("VALID");
            changed = true;
        }
        if (changed) {
            user.setGmtModified(nowDate());
            getUserMapper().updateById(user);
            MemoryCacheManage.remove(CacheKey.getLoginUserKey(user.getId()));
        }
    }

    private void disableUser(DbhubUserDO user, UserIdentityBindingDO binding, OidcAuthenticatedProfile profile) {
        user.setStatus("INVALID");
        user.setGmtModified(nowDate());
        getUserMapper().updateById(user);
        updateBinding(binding, user.getId(), profile, SsoConstants.BINDING_STATUS_DISABLED);
        MemoryCacheManage.remove(CacheKey.getLoginUserKey(user.getId()));
    }

    private boolean isDisabled(OidcAuthenticatedProfile profile) {
        return StringUtils.isBlank(profile.getSubject());
    }

    private String defaultUserName(OidcAuthenticatedProfile profile) {
        if (StringUtils.isNotBlank(profile.getUserName())) {
            return profile.getUserName();
        }
        if (StringUtils.isNotBlank(profile.getEmail())) {
            return profile.getEmail();
        }
        return "oidc_" + DigestUtil.md5Hex(profile.getIssuer() + ":" + profile.getSubject()).substring(0, 16);
    }

    private String defaultNickName(OidcAuthenticatedProfile profile) {
        if (StringUtils.isNotBlank(profile.getDisplayName())) {
            return profile.getDisplayName();
        }
        if (StringUtils.isNotBlank(profile.getUserName())) {
            return profile.getUserName();
        }
        if (StringUtils.isNotBlank(profile.getEmail())) {
            return profile.getEmail();
        }
        return profile.getSubject();
    }

    private Date nowDate() {
        return new Date();
    }
}
