package io.torana.api.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents who caused an audited action.
 *
 * <p>An actor can be a user, service account, system process, or anonymous.
 *
 * <p>This is an immutable value object.
 */
public record Actor(String id, ActorType type, String displayName, Map<String, String> attributes) {

    public Actor {
        Objects.requireNonNull(id, "Actor id must not be null");
        Objects.requireNonNull(type, "Actor type must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * Creates a user actor.
     *
     * @param id the user identifier
     * @param displayName the display name
     * @return a new Actor with type USER
     */
    public static Actor user(String id, String displayName) {
        return new Actor(id, ActorType.USER, displayName, Map.of());
    }

    /**
     * Creates a user actor with additional attributes.
     *
     * @param id the user identifier
     * @param displayName the display name
     * @param attributes additional attributes (e.g., email, department)
     * @return a new Actor with type USER
     */
    public static Actor user(String id, String displayName, Map<String, String> attributes) {
        return new Actor(id, ActorType.USER, displayName, attributes);
    }

    /**
     * Creates a service account actor.
     *
     * @param id the service account identifier
     * @param displayName the display name
     * @return a new Actor with type SERVICE_ACCOUNT
     */
    public static Actor serviceAccount(String id, String displayName) {
        return new Actor(id, ActorType.SERVICE_ACCOUNT, displayName, Map.of());
    }

    /**
     * Creates a system actor.
     *
     * @param id the system identifier
     * @return a new Actor with type SYSTEM
     */
    public static Actor system(String id) {
        return new Actor(id, ActorType.SYSTEM, id, Map.of());
    }

    /**
     * Creates an anonymous actor.
     *
     * @return a new Actor with type ANONYMOUS
     */
    public static Actor anonymous() {
        return new Actor("anonymous", ActorType.ANONYMOUS, "Anonymous", Map.of());
    }

    /**
     * Returns a copy of this actor with an additional attribute.
     *
     * @param key the attribute key
     * @param value the attribute value
     * @return a new Actor with the additional attribute
     */
    public Actor withAttribute(String key, String value) {
        var newAttributes = new HashMap<>(attributes);
        newAttributes.put(key, value);
        return new Actor(id, type, displayName, newAttributes);
    }
}
