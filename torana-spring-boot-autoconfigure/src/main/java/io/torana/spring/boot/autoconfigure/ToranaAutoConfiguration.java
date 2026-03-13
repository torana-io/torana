package io.torana.spring.boot.autoconfigure;

import io.torana.core.AuditEntryFactory;
import io.torana.core.AuditPipeline;
import io.torana.core.ContextCollector;
import io.torana.core.ReflectionSnapshotProvider;
import io.torana.core.TransactionAwareWriter;
import io.torana.core.diff.DefaultDiffEngine;
import io.torana.core.redaction.DefaultRedactionPolicy;
import io.torana.jdbc.JdbcAuditWriter;
import io.torana.jdbc.dialect.DialectDetector;
import io.torana.jdbc.dialect.SqlDialect;
import io.torana.spi.ActorResolver;
import io.torana.spi.AuditWriter;
import io.torana.spi.DiffEngine;
import io.torana.spi.RedactionPolicy;
import io.torana.spi.RequestContextResolver;
import io.torana.spi.SnapshotProvider;
import io.torana.spi.TenantResolver;
import io.torana.spi.TraceResolver;
import io.torana.spring.aop.AuditedActionAspect;
import io.torana.spring.aop.SpringTransactionAwareWriter;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import javax.sql.DataSource;

/**
 * Auto-configuration for Torana audit trail.
 *
 * <p>Automatically configures:
 *
 * <ul>
 *   <li>JDBC-based audit writer
 *   <li>AOP aspect for @AuditedAction annotation
 *   <li>Context resolvers (actor, tenant, request, trace)
 *   <li>Diff engine and redaction policy
 * </ul>
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "torana",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(ToranaProperties.class)
public class ToranaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public SqlDialect toranaDialect(DataSource dataSource) {
        return DialectDetector.detect(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DataSource.class, SqlDialect.class})
    public JdbcTemplate toranaJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(AuditWriter.class)
    @ConditionalOnBean({JdbcTemplate.class, SqlDialect.class})
    public AuditWriter toranaAuditWriter(
            JdbcTemplate jdbcTemplate, SqlDialect dialect, ToranaProperties properties) {
        return new JdbcAuditWriter(jdbcTemplate, dialect, properties.getTableName());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(TransactionSynchronizationManager.class)
    public TransactionAwareWriter toranaTransactionAwareWriter(AuditWriter auditWriter) {
        return new SpringTransactionAwareWriter(auditWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public SnapshotProvider toranaSnapshotProvider(ToranaProperties properties) {
        return new ReflectionSnapshotProvider(properties.getSnapshot().getMaxDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public DiffEngine toranaDiffEngine() {
        return new DefaultDiffEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "torana.redaction",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public RedactionPolicy toranaRedactionPolicy(ToranaProperties properties) {
        ToranaProperties.RedactionProperties redaction = properties.getRedaction();
        List<String> regexPatterns = java.util.Arrays.asList(redaction.getPatterns());
        // Use empty set for exact fields - all patterns are regex-based
        return new DefaultRedactionPolicy(java.util.Set.of(), regexPatterns);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextCollector toranaContextCollector(
            List<ActorResolver> actorResolvers,
            List<TenantResolver> tenantResolvers,
            List<RequestContextResolver> requestContextResolvers,
            List<TraceResolver> traceResolvers) {
        return new ContextCollector(
                actorResolvers, tenantResolvers, requestContextResolvers, traceResolvers);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditEntryFactory toranaAuditEntryFactory(DiffEngine diffEngine) {
        return new AuditEntryFactory(diffEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditPipeline toranaAuditPipeline(
            ContextCollector contextCollector,
            AuditEntryFactory entryFactory,
            RedactionPolicy redactionPolicy,
            TransactionAwareWriter transactionAwareWriter) {
        return new AuditPipeline(
                contextCollector, entryFactory, redactionPolicy, transactionAwareWriter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditedActionAspect toranaAuditedActionAspect(
            AuditPipeline auditPipeline, SnapshotProvider snapshotProvider) {
        return new AuditedActionAspect(auditPipeline, snapshotProvider);
    }
}
