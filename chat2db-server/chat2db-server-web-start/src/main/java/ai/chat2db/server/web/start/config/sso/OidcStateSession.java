package ai.chat2db.server.web.start.config.sso;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcStateSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String state;

    private String nonce;

    private String callback;
}
