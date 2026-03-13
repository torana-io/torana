package io.torana.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TargetTest {

    @Test
    void shouldCreateTargetWithTypeAndId() {
        Target target = Target.of("Order", "order-123");

        assertThat(target.type()).isEqualTo("Order");
        assertThat(target.id()).isEqualTo("order-123");
        assertThat(target.displayName()).isNull();
    }

    @Test
    void shouldCreateTargetWithDisplayName() {
        Target target = Target.of("Order", "order-123", "Order #123");

        assertThat(target.type()).isEqualTo("Order");
        assertThat(target.id()).isEqualTo("order-123");
        assertThat(target.displayName()).isEqualTo("Order #123");
    }

    @Test
    void shouldGenerateReference() {
        Target target = Target.of("Order", "order-123");

        assertThat(target.reference()).isEqualTo("Order:order-123");
    }

    @Test
    void shouldAddDisplayName() {
        Target target = Target.of("Order", "order-123").withDisplayName("Order #123");

        assertThat(target.displayName()).isEqualTo("Order #123");
        assertThat(target.type()).isEqualTo("Order");
        assertThat(target.id()).isEqualTo("order-123");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> Target.of(null, "order-123"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type must not be null");
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> Target.of("Order", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldBeEqualWhenFieldsMatch() {
        Target target1 = Target.of("Order", "order-123");
        Target target2 = Target.of("Order", "order-123");

        assertThat(target1).isEqualTo(target2);
        assertThat(target1.hashCode()).isEqualTo(target2.hashCode());
    }
}
