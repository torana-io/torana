package io.torana.spring.aop.testapp;

import io.torana.api.AuditedCreate;
import io.torana.api.AuditedDelete;
import io.torana.api.AuditedUpdate;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Test service demonstrating preset audit annotations.
 *
 * <p>This service is used in integration tests to verify that @AuditedCreate, @AuditedUpdate, and
 * @AuditedDelete annotations work correctly with the AOP aspect.
 */
@Service
public class OrderService {

    private final Map<String, Order> orders = new HashMap<>();

    /**
     * Creates a new order using @AuditedCreate annotation.
     *
     * <p>This tests: - Action name "entity.created" is used - Target ID extraction from result -
     * Metadata fields population - Snapshot capture if enabled
     */
    @AuditedCreate(
            targetType = "Order",
            targetId = "#result.id",
            targetDisplayName = "#result.orderNumber",
            metadataFields = {
                "customerId:#result.customerId",
                "total:#result.total",
                "operation:'create'"
            },
            tags = {"financial", "customer-facing"})
    public Order createOrder(String customerId, BigDecimal total) {
        String id = UUID.randomUUID().toString();
        String orderNumber = "ORD-" + System.currentTimeMillis();

        Order order = new Order(id, orderNumber, customerId, total, "PENDING");
        orders.put(id, order);

        return order;
    }

    /**
     * Creates an order with snapshot capture enabled.
     */
    @AuditedCreate(
            targetType = "Order",
            targetId = "#result.id",
            captureChanges = true,
            snapshotSource = "#result",
            metadataFields = {"customerId:#result.customerId"})
    public Order createOrderWithSnapshot(String customerId, BigDecimal total) {
        return createOrder(customerId, total);
    }

    /**
     * Updates an existing order using @AuditedUpdate annotation.
     *
     * <p>This tests: - Action name "entity.updated" is used - Change tracking with snapshots -
     * Before/after comparison - Parameter-based target ID
     */
    @AuditedUpdate(
            targetType = "Order",
            targetId = "#order.id",
            targetDisplayName = "#order.orderNumber",
            captureChanges = true,
            snapshotSource = "#order",
            metadataFields = {"newStatus:#newStatus", "operation:'update'"})
    public Order updateOrderStatus(Order order, String newStatus) {
        order.setStatus(newStatus);
        orders.put(order.getId(), order);
        return order;
    }

    /**
     * Updates order with amount change.
     */
    @AuditedUpdate(
            targetType = "Order",
            targetId = "#order.id",
            captureChanges = true,
            snapshotSource = "#order",
            metadataFields = {"oldTotal:#order.total", "newTotal:#newTotal"})
    public Order updateOrderTotal(Order order, BigDecimal newTotal) {
        order.setTotal(newTotal);
        orders.put(order.getId(), order);
        return order;
    }

    /**
     * Deletes an order using @AuditedDelete annotation.
     *
     * <p>This tests: - Action name "entity.deleted" is used - Target ID from parameter - Optional
     * snapshot capture before deletion
     */
    @AuditedDelete(
            targetType = "Order",
            targetId = "#orderId",
            metadataFields = {"reason:#reason", "operation:'delete'"})
    public void deleteOrder(String orderId, String reason) {
        orders.remove(orderId);
    }

    /**
     * Deletes order with snapshot of entity before deletion.
     */
    @AuditedDelete(
            targetType = "Order",
            targetId = "#order.id",
            targetDisplayName = "#order.orderNumber",
            captureChanges = true,
            snapshotSource = "#order",
            metadataFields = {"reason:#reason"})
    public void deleteOrderWithSnapshot(Order order, String reason) {
        orders.remove(order.getId());
    }

    /**
     * Creates an order that throws an exception to test failure recording.
     */
    @AuditedCreate(
            targetType = "Order",
            targetId = "#customerId",
            recordFailures = true,
            metadataFields = {"customerId:#customerId"})
    public Order createOrderThatFails(String customerId, BigDecimal total) {
        throw new RuntimeException("Intentional failure for testing");
    }

    /**
     * Creates an order that throws an exception but doesn't record the failure.
     */
    @AuditedCreate(
            targetType = "Order",
            targetId = "#customerId",
            recordFailures = false,
            metadataFields = {"customerId:#customerId"})
    public Order createOrderWithoutFailureRecording(String customerId, BigDecimal total) {
        throw new RuntimeException("Failure without audit recording");
    }

    // Helper methods for tests

    public Order getOrder(String id) {
        return orders.get(id);
    }

    public void clear() {
        orders.clear();
    }
}
