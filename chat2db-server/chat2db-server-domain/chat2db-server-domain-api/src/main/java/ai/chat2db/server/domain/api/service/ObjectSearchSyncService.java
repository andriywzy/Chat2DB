package ai.chat2db.server.domain.api.service;

public interface ObjectSearchSyncService {

    void requestSync(Long dataSourceId);

    void removeDataSource(Long dataSourceId);
}
