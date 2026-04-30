package ai.chat2db.server.start.test.core;

import ai.chat2db.server.domain.api.model.DataSource;
import ai.chat2db.server.domain.api.service.DataSourceAccessBusinessService;
import ai.chat2db.server.domain.repository.Dbutils;
import ai.chat2db.server.start.test.TestApplication;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.common.model.Context;
import ai.chat2db.server.tools.common.model.LoginUser;
import ai.chat2db.server.tools.common.util.ContextUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DataSourceAccessBusinessServiceTest extends TestApplication {

    @Autowired
    private DataSourceAccessBusinessService dataSourceAccessBusinessService;

    /**
     * Project permission is now the primary access model.
     * Admin users are still allowed, and non-admin users are checked against
     * project/team access before falling back to any legacy compatibility rules.
     */
    @Test
    public void testCheckPermission() {
//        userLoginIdentity(false, 3L);
        userLoginIdentity(true, 2L);

        DataSource source = new DataSource();
        source.setUserId(5L);
        source.setId(3L);

        ActionResult actionResult = dataSourceAccessBusinessService.checkPermission(source);
        assertNotNull(actionResult);
    }

    /**
     * Save the current user identity (administrator or normal user) and user ID to the context and database session for subsequent use.
     *
     * @param isAdmin
     * @param userId
     */
    private static void userLoginIdentity(boolean isAdmin, Long userId) {
        Context context = Context.builder().loginUser(
                LoginUser.builder().admin(isAdmin).id(userId).build()
        ).build();
        ContextUtils.setContext(context);
        Dbutils.setSession();
    }
}
