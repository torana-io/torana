package io.torana.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditActionTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "order.cancelled",
                "invoice.approved",
                "user.role.assigned",
                "payment.refund.requested",
                "simple",
                "a1.b2.c3"
            })
    void shouldAcceptValidActionNames(String name) {
        AuditAction action = AuditAction.of(name);
        assertThat(action.name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Order.Cancelled", // uppercase letters
                "order_cancelled", // underscore
                "order-cancelled", // hyphen
                ".order", // leading dot
                "order.", // trailing dot
                "order..cancelled", // double dot
                "1order", // starts with number
                "order.1cancelled", // segment starts with number
                "" // empty string
            })
    void shouldRejectInvalidActionNames(String name) {
        assertThatThrownBy(() -> AuditAction.of(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullActionName() {
        assertThatThrownBy(() -> AuditAction.of(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldRejectBlankActionName() {
        assertThatThrownBy(() -> AuditAction.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldExtractCategory() {
        assertThat(AuditAction.of("order.cancelled").category()).isEqualTo("order");
        assertThat(AuditAction.of("user.role.assigned").category()).isEqualTo("user");
        assertThat(AuditAction.of("simple").category()).isEqualTo("simple");
    }

    @Test
    void shouldCheckStartsWith() {
        AuditAction action = AuditAction.of("order.cancelled");
        assertThat(action.startsWith("order")).isTrue();
        assertThat(action.startsWith("order.")).isTrue();
        assertThat(action.startsWith("invoice")).isFalse();
    }

    @Test
    void shouldReturnNameFromToString() {
        AuditAction action = AuditAction.of("order.cancelled");
        assertThat(action.toString()).isEqualTo("order.cancelled");
    }

    @Test
    void shouldBeEqualWhenNamesMatch() {
        AuditAction action1 = AuditAction.of("order.cancelled");
        AuditAction action2 = AuditAction.of("order.cancelled");
        assertThat(action1).isEqualTo(action2);
        assertThat(action1.hashCode()).isEqualTo(action2.hashCode());
    }
}
