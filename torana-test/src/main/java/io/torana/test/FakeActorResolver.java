package io.torana.test;

import io.torana.api.model.Actor;
import io.torana.spi.ActorResolver;

import java.util.Optional;

/**
 * Fake actor resolver for testing.
 *
 * <p>Allows setting a specific actor to be returned, or no actor.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * FakeActorResolver resolver = new FakeActorResolver();
 * resolver.setActor(Actor.user("alice", "Alice Smith"));
 *
 * // Later, in cleanup or other tests:
 * resolver.clearActor();
 * }</pre>
 */
public class FakeActorResolver implements ActorResolver {

    private Actor actor;

    /** Creates a resolver with a default test user. */
    public FakeActorResolver() {
        this.actor = Actor.user("test-user", "Test User");
    }

    /**
     * Creates a resolver with the specified actor.
     *
     * @param actor the actor to return
     */
    public FakeActorResolver(Actor actor) {
        this.actor = actor;
    }

    @Override
    public Optional<Actor> resolve() {
        return Optional.ofNullable(actor);
    }

    /**
     * Sets the actor to return.
     *
     * @param actor the actor
     */
    public void setActor(Actor actor) {
        this.actor = actor;
    }

    /** Clears the actor (resolve will return empty). */
    public void clearActor() {
        this.actor = null;
    }

    /**
     * Sets a user actor.
     *
     * @param id the user ID
     * @param displayName the display name
     */
    public void setUser(String id, String displayName) {
        this.actor = Actor.user(id, displayName);
    }

    /** Sets an anonymous actor. */
    public void setAnonymous() {
        this.actor = Actor.anonymous();
    }
}
