package ai.chat2db.server.web.api.controller.data.source.vo;

import java.util.List;

import ai.chat2db.server.common.api.controller.vo.SimpleEnvironmentVO;
import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.KeyValue;
import ai.chat2db.spi.model.SSHInfo;
import lombok.Data;

/**
 * @author moji
 * @version ConnectionVO.java, v 0.1 September 16, 2022 14:15 moji Exp $
 * @date 2022/09/16
 */
@Data
public class DataSourceVO {

    /**
     * primary key id
     */
    private Long id;

    /**
     * Connection alias
     */
    private String alias;

    /**
     * connection address
     */
    private String url;

    /**
     * Connect users
     */
    private String user;

    /**
     * password
     */
    private String password;

    /**
     * Certification type
     */
    private String authenticationType;
    /**
     * Connection Type
     */
    private String type;

    /**
     * environment type
     */
    private String envType;

    /**
     * host
     */
    private String host;

    /**
     * port
     */
    private String port;

    /**
     * ssh
     */
    private SSHInfo ssh;

    ///**
    // * ssh
    // */
    //private SSLInfo ssl;

    /**
     * sid
     */
    private String sid;

    /**
     * driver
     */
    private String driver;

    /**
     * jdbc version
     */
    private String jdbc;


    /**
     * Extended Information
     */
    private List<KeyValue> extendInfo;

    /**
     * Driver configuration
     */
    private DriverConfig driverConfig;

    /**
     * environment id
     */
    private Long environmentId;

    /**
     * environment
     */
    private SimpleEnvironmentVO environment;

    /**
     * service name
     */
    private String serviceName;

    /**
     * Service type
     */
    private String serviceType;

    /**
     * project id
     */
    private Long projectId;

    /**
     * project name
     */
    private String projectName;

    /**
     * Effective access scope for display.
     */
    private String accessScope;

    /**
     * Whether the current user can manage datasource config.
     */
    private Boolean canManage;

    /**
     * Whether to support database
     */
    private boolean supportDatabase;

    /**
     * Whether to support schema
     */
    private boolean supportSchema;
}
