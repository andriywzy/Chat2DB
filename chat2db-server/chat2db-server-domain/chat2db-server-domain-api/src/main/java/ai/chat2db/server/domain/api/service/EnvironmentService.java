package ai.chat2db.server.domain.api.service;

import java.util.List;

import ai.chat2db.server.domain.api.model.Environment;
import ai.chat2db.server.tools.base.wrapper.result.ActionResult;
import ai.chat2db.server.tools.base.wrapper.result.DataResult;
import ai.chat2db.server.domain.api.param.EnvironmentPageQueryParam;
import ai.chat2db.server.tools.base.wrapper.result.ListResult;
import ai.chat2db.server.tools.base.wrapper.result.PageResult;

/**
 * environment
 *
 * @author Jiaju Zhuang
 */
public interface EnvironmentService {

    /**
     * List Query Data
     *
     * @param idList
     * @return
     */
    ListResult<Environment> listQuery(List<Long> idList);

    /**
     * Paging Query Data
     *
     * @param param
     * @return
     */
    PageResult<Environment> pageQuery(EnvironmentPageQueryParam param);

    /**
     * Query all environments available for the current phase.
     *
     * @return environments
     */
    ListResult<Environment> queryList();

    /**
     * Query single environment.
     *
     * @param id environment id
     * @return environment
     */
    DataResult<Environment> query(Long id);

    /**
     * Create environment.
     *
     * @param environment environment payload
     * @return id
     */
    DataResult<Long> create(Environment environment);

    /**
     * Update environment.
     *
     * @param environment environment payload
     * @return id
     */
    DataResult<Long> update(Environment environment);

    /**
     * Delete environment.
     *
     * @param id environment id
     * @return action result
     */
    ActionResult delete(Long id);

}
