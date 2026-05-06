package ai.chat2db.server.web.api.controller.data.source.request;

import java.util.List;

import ai.chat2db.spi.config.DriverConfig;
import ai.chat2db.spi.model.KeyValue;
import ai.chat2db.spi.model.SSHInfo;
import lombok.Data;

@Data
public class DataSourceImportItemRequest {

    private String alias;

    private String url;

    private String user;

    private String password;

    private String type;

    private String host;

    private String port;

    private SSHInfo ssh;

    private String sid;

    private String driver;

    private String jdbc;

    private List<KeyValue> extendInfo;

    private DriverConfig driverConfig;

    private Long environmentId;

    private String environmentName;

    private String environmentShortName;

    private Long projectId;

    private String projectName;

    private String serviceName;

    private String serviceType;
}
