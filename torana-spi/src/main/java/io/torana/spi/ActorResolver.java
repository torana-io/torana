package io.torana.spi;

import io.torana.api.model.Actor;

import java.util.Optional;

/**
 * SPI for resolving the current actor (who is performing the action).
 *
 * <p>Implementations typically extract actor information from security contexts, request headers,
 * or other contextual sources.
 *
 * <p>Multiple resolvers can be registered. The first one that returns a non-empty result wins.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class SecurityContextActorResolver implements ActorResolver {
 *     @Override
 *     public Optional<Actor> resolve() {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth == null || !auth.isAuthenticated()) {
 *             return Optional.empty();
 *         }
 *         return Optional.of(Actor.user(auth.getName(), auth.getName()));
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ActorResolver {

    /**
     * Resolves the current actor from the execution context.
     *
     * @return the resolved actor, or empty if not available
     */
    Optional<Actor> resolve();
}
