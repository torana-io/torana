package io.torana.api.model;

import java.util.Map;

/**
 * Represents HTTP request metadata captured at audit time.
 *
 * <p>This provides context about the HTTP request that triggered the audited action.
 *
 * <p>This is an immutable value object.
 */
public record RequestContext(
        String requestId,
        String method,
        String path,
        String clientIp,
        String userAgent,
        Map<String, String> headers) {

    private static final RequestContext EMPTY =
            new RequestContext(null, null, null, null, null, Map.of());

    public RequestContext {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Returns an empty request context.
     *
     * @return an empty RequestContext
     */
    public static RequestContext empty() {
        return EMPTY;
    }

    /**
     * Creates a basic request context with method and path.
     *
     * @param method the HTTP method
     * @param path the request path
     * @return a new RequestContext
     */
    public static RequestContext of(String method, String path) {
        return new RequestContext(null, method, path, null, null, Map.of());
    }

    /**
     * Creates a request context builder.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this context has a request ID.
     *
     * @return true if requestId is not null
     */
    public boolean hasRequestId() {
        return requestId != null;
    }

    /** Builder for RequestContext. */
    public static class Builder {
        private String requestId;
        private String method;
        private String path;
        private String clientIp;
        private String userAgent;
        private Map<String, String> headers = Map.of();

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(requestId, method, path, clientIp, userAgent, headers);
        }
    }
}
