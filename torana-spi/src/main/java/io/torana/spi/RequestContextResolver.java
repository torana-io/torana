package io.torana.spi;

import io.torana.api.model.RequestContext;

import java.util.Optional;

/**
 * SPI for capturing HTTP request context.
 *
 * <p>Implementations typically extract request information from the current web request (method,
 * path, headers, client IP, etc.).
 *
 * <p>Multiple resolvers can be registered. The first one that returns a non-empty result wins.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class WebMvcRequestContextResolver implements RequestContextResolver {
 *     @Override
 *     public Optional<RequestContext> resolve() {
 *         ServletRequestAttributes attrs = (ServletRequestAttributes)
 *             RequestContextHolder.getRequestAttributes();
 *         if (attrs == null) return Optional.empty();
 *
 *         HttpServletRequest request = attrs.getRequest();
 *         return Optional.of(RequestContext.builder()
 *             .method(request.getMethod())
 *             .path(request.getRequestURI())
 *             .clientIp(request.getRemoteAddr())
 *             .build());
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface RequestContextResolver {

    /**
     * Resolves the HTTP request context.
     *
     * @return the resolved request context, or empty if not available
     */
    Optional<RequestContext> resolve();
}
