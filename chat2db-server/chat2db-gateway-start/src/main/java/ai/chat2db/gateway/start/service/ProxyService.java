package ai.chat2db.gateway.start.service;

import ai.chat2db.gateway.start.config.GatewayProperties;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;

@Service
public class ProxyService {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Resource
    private GatewayProperties gatewayProperties;

    public ResponseEntity<byte[]> proxy(HttpServletRequest request, byte[] requestBody) throws IOException, InterruptedException {
        String upstreamBaseUrl = StringUtils.removeEnd(gatewayProperties.getUpstreamBaseUrl(), "/");
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        String target = upstreamBaseUrl + requestUri + (StringUtils.isBlank(queryString) ? "" : "?" + queryString);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(target))
            .timeout(TIMEOUT)
            .method(request.getMethod(), buildBodyPublisher(requestBody));

        copyHeaders(request, builder);

        HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> headers.put(name, values));
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        return new ResponseEntity<>(response.body(), headers, HttpStatusCode.valueOf(response.statusCode()));
    }

    private HttpRequest.BodyPublisher buildBodyPublisher(byte[] requestBody) {
        if (requestBody == null || requestBody.length == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(requestBody);
    }

    private void copyHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HttpHeaders.HOST.equalsIgnoreCase(name) || HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
    }
}
