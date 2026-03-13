package io.torana.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ActorTest {

    @Test
    void shouldCreateUserActor() {
        Actor actor = Actor.user("user-123", "Alice Smith");

        assertThat(actor.id()).isEqualTo("user-123");
        assertThat(actor.type()).isEqualTo(ActorType.USER);
        assertThat(actor.displayName()).isEqualTo("Alice Smith");
        assertThat(actor.attributes()).isEmpty();
    }

    @Test
    void shouldCreateUserActorWithAttributes() {
        Map<String, String> attributes =
                Map.of("email", "alice@example.com", "department", "Engineering");
        Actor actor = Actor.user("user-123", "Alice Smith", attributes);

        assertThat(actor.id()).isEqualTo("user-123");
        assertThat(actor.attributes()).containsEntry("email", "alice@example.com");
        assertThat(actor.attributes()).containsEntry("department", "Engineering");
    }

    @Test
    void shouldCreateServiceAccountActor() {
        Actor actor = Actor.serviceAccount("svc-api-client", "API Client Service");

        assertThat(actor.id()).isEqualTo("svc-api-client");
        assertThat(actor.type()).isEqualTo(ActorType.SERVICE_ACCOUNT);
        assertThat(actor.displayName()).isEqualTo("API Client Service");
    }

    @Test
    void shouldCreateSystemActor() {
        Actor actor = Actor.system("scheduler");

        assertThat(actor.id()).isEqualTo("scheduler");
        assertThat(actor.type()).isEqualTo(ActorType.SYSTEM);
        assertThat(actor.displayName()).isEqualTo("scheduler");
    }

    @Test
    void shouldCreateAnonymousActor() {
        Actor actor = Actor.anonymous();

        assertThat(actor.id()).isEqualTo("anonymous");
        assertThat(actor.type()).isEqualTo(ActorType.ANONYMOUS);
        assertThat(actor.displayName()).isEqualTo("Anonymous");
    }

    @Test
    void shouldAddAttribute() {
        Actor actor = Actor.user("user-123", "Alice").withAttribute("email", "alice@example.com");

        assertThat(actor.attributes()).containsEntry("email", "alice@example.com");
    }

    @Test
    void shouldPreserveExistingAttributesWhenAddingNew() {
        Actor actor =
                Actor.user("user-123", "Alice", Map.of("role", "admin"))
                        .withAttribute("email", "alice@example.com");

        assertThat(actor.attributes()).containsEntry("role", "admin");
        assertThat(actor.attributes()).containsEntry("email", "alice@example.com");
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> new Actor(null, ActorType.USER, "Test", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new Actor("user-123", null, "Test", Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type must not be null");
    }

    @Test
    void shouldMakeAttributesImmutable() {
        Actor actor = Actor.user("user-123", "Alice");

        assertThatThrownBy(() -> actor.attributes().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldBeEqualWhenAllFieldsMatch() {
        Actor actor1 = Actor.user("user-123", "Alice");
        Actor actor2 = Actor.user("user-123", "Alice");

        assertThat(actor1).isEqualTo(actor2);
        assertThat(actor1.hashCode()).isEqualTo(actor2.hashCode());
    }
}
