package io.torana.spring.webmvc;

import io.torana.api.model.RequestContext;
import io.torana.spi.RequestContextResolver;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Request context resolver that extracts information from Spring WebMVC's RequestContextHolder.
 *
 * <p>Captures:
 *
 * <ul>
 *   <li>HTTP method (GET, POST, etc.)
 *   <li>Request path
 *   <li>Client IP address (with proxy support)
 *   <li>User agent
 *   <li>Request ID (from headers or generated)
 *   <li>Configurable headers
 * </ul>
 */
public class WebMvcRequestContextResolver implements RequestContextResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_REQUEST_ID = "X-Request-ID";
    private static final String X_CORRELATION_ID = "X-Correlation-ID";

    private static final Set<String> DEFAULT_CAPTURED_HEADERS =
            Set.of("Accept", "Accept-Language", "Content-Type", "Origin", "Referer");

    private final Set<String> capturedHeaders;

    public WebMvcRequestContextResolver() {
        this(DEFAULT_CAPTURED_HEADERS);
    }

    public WebMvcRequestContextResolver(Set<String> capturedHeaders) {
        this.capturedHeaders = capturedHeaders;
    }

    @Override
    public Optional<RequestContext> resolve() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            return Optional.empty();
        }

        HttpServletRequest request = attributes.getRequest();
        return Optional.of(buildRequestContext(request));
    }

    private RequestContext buildRequestContext(HttpServletRequest request) {
        return RequestContext.builder()
                .requestId(resolveRequestId(request))
                .method(request.getMethod())
                .path(request.getRequestURI())
                .clientIp(resolveClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .headers(captureHeaders(request))
                .build();
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(X_REQUEST_ID);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        requestId = request.getHeader(X_CORRELATION_ID);
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] ips = forwardedFor.split(",");
            return ips[0].trim();
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private Map<String, String> captureHeaders(HttpServletRequest request) {
        if (capturedHeaders.isEmpty()) {
            return Map.of();
        }

        Map<String, String> headers = new HashMap<>();
        for (String headerName : capturedHeaders) {
            String value = request.getHeader(headerName);
            if (value != null && !value.isBlank()) {
                headers.put(headerName, value);
            }
        }
        return Collections.unmodifiableMap(headers);
    }
}
