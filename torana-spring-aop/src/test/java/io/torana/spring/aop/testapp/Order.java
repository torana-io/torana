package io.torana.spring.aop.testapp;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Test entity for integration testing preset annotations.
 */
public class Order {
    private String id;
    private String orderNumber;
    private String customerId;
    private BigDecimal total;
    private String status;

    public Order() {}

    public Order(String id, String orderNumber, String customerId, BigDecimal total, String status) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.total = total;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(id, order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", customerId='" + customerId + '\'' +
                ", total=" + total +
                ", status='" + status + '\'' +
                '}';
    }
}
