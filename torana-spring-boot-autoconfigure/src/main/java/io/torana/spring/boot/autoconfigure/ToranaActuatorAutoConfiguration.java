package io.torana.spring.boot.autoconfigure;

import io.torana.jdbc.dialect.SqlDialect;
import io.torana.spring.boot.autoconfigure.actuator.AuditTrailHealthIndicator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for Spring Boot Actuator integration.
 *
 * <p>Provides health indicators and management endpoints for monitoring the Torana audit system.
 *
 * <p>Automatically activated when:
 *
 * <ul>
 *   <li>Spring Boot Actuator is on the classpath ({@code HealthIndicator} class present)
 *   <li>{@code management.health.audit-trail.enabled=true} (default)
 * </ul>
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,metrics
 *   health:
 *     audit-trail:
 *       enabled: true
 *
 * torana:
 *   metrics:
 *     health-check-window-seconds: 60
 *     error-rate-threshold: 0.1
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnEnabledHealthIndicator("auditTrail")
@EnableConfigurationProperties(ToranaProperties.class)
public class ToranaActuatorAutoConfiguration {

    /**
     * Creates the audit trail health indicator.
     *
     * <p>This health indicator monitors:
     *
     * <ul>
     *   <li>Database connectivity
     *   <li>Audit table accessibility
     *   <li>Error rates over a configured time window
     * </ul>
     *
     * <p>Health status:
     *
     * <ul>
     *   <li><strong>UP:</strong> System healthy, error rate below threshold
     *   <li><strong>WARNING:</strong> Error rate exceeds threshold
     *   <li><strong>DOWN:</strong> Database unavailable
     * </ul>
     *
     * @param jdbcTemplate JDBC template for database queries
     * @param dialect SQL dialect for the configured database
     * @param properties Torana configuration properties
     * @return configured health indicator
     */
    @Bean
    @ConditionalOnBean({JdbcTemplate.class, SqlDialect.class})
    public AuditTrailHealthIndicator auditTrailHealthIndicator(
            JdbcTemplate jdbcTemplate, SqlDialect dialect, ToranaProperties properties) {

        ToranaProperties.MetricsProperties metricsProps = properties.getMetrics();

        return new AuditTrailHealthIndicator(
                jdbcTemplate,
                dialect,
                properties.getTableName(),
                metricsProps.getHealthCheckWindowSeconds(),
                metricsProps.getErrorRateThreshold());
    }
}
