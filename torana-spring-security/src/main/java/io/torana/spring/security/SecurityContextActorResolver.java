package io.torana.spring.security;

import io.torana.api.model.Actor;
import io.torana.api.model.ActorType;
import io.torana.spi.ActorResolver;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Actor resolver that extracts actor information from Spring Security's SecurityContext.
 *
 * <p>Supports various authentication types:
 *
 * <ul>
 *   <li>UserDetails-based authentication
 *   <li>Anonymous authentication (returns anonymous actor)
 *   <li>Generic authentication (uses principal name)
 * </ul>
 *
 * <p>For OAuth2/JWT support, use the OAuth2 extension module.
 */
public class SecurityContextActorResolver implements ActorResolver {

    @Override
    public Optional<Actor> resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication instanceof AnonymousAuthenticationToken) {
            return Optional.of(Actor.anonymous());
        }

        Object principal = authentication.getPrincipal();
        return Optional.of(resolveFromPrincipal(principal, authentication));
    }

    private Actor resolveFromPrincipal(Object principal, Authentication authentication) {
        // Handle UserDetails
        if (principal instanceof UserDetails userDetails) {
            return createActorFromUserDetails(userDetails);
        }

        // Fallback to principal name
        String name = authentication.getName();
        Map<String, String> attributes = extractAuthoritiesAsAttributes(authentication);
        return new Actor(name, ActorType.USER, name, attributes);
    }

    private Actor createActorFromUserDetails(UserDetails userDetails) {
        String username = userDetails.getUsername();
        Map<String, String> attributes = new HashMap<>();

        // Add authorities as attribute
        if (!userDetails.getAuthorities().isEmpty()) {
            String authorities = userDetails.getAuthorities().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            attributes.put("authorities", authorities);
        }

        return new Actor(username, ActorType.USER, username, attributes);
    }

    private Map<String, String> extractAuthoritiesAsAttributes(Authentication authentication) {
        Map<String, String> attributes = new HashMap<>();
        if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
            String authorities = authentication.getAuthorities().stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            attributes.put("authorities", authorities);
        }
        return attributes;
    }
}
