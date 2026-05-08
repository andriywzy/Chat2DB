package ai.chat2db.server.common.api.audit;

import ai.chat2db.server.domain.api.enums.AuditActionTypeEnum;
import ai.chat2db.server.domain.api.enums.AuditResourceTypeEnum;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAudit {

    AuditActionTypeEnum actionType();

    AuditResourceTypeEnum resourceType();
}
