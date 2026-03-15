package io.torana.spring.aop;

import io.torana.api.AuditedAction;
import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.Target;
import io.torana.core.AuditContext;
import io.torana.core.AuditPipeline;
import io.torana.spi.SnapshotProvider;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aspect that intercepts methods annotated with {@link AuditedAction}.
 *
 * <p>This aspect:
 *
 * <ol>
 *   <li>Extracts audit information from the annotation
 *   <li>Evaluates SpEL expressions for target ID, metadata, etc.
 *   <li>Captures before/after snapshots if configured
 *   <li>Processes the audit entry through the pipeline
 * </ol>
 */
@Aspect
public class AuditedActionAspect {

    private static final Pattern METADATA_PATTERN =
            Pattern.compile("([\\w.]+)\\s*=\\s*(.+?)(?:,|$)");

    private final AuditPipeline auditPipeline;
    private final SnapshotProvider snapshotProvider;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    public AuditedActionAspect(AuditPipeline auditPipeline) {
        this(auditPipeline, null);
    }

    public AuditedActionAspect(AuditPipeline auditPipeline, SnapshotProvider snapshotProvider) {
        this.auditPipeline = auditPipeline;
        this.snapshotProvider = snapshotProvider;
    }

    @Around("@annotation(auditedAction)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditedAction auditedAction)
            throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        EvaluationContext evaluationContext = createEvaluationContext(method, args);

        AuditContext context = new AuditContext();
        context.markStarted();
        context.setAction(AuditAction.of(auditedAction.value()));

        String targetType = auditedAction.targetType();
        String targetIdExpr = auditedAction.targetId();
        String targetDisplayNameExpr = auditedAction.targetDisplayName();

        if (!targetType.isEmpty() && !targetIdExpr.isEmpty()) {
            String targetId = evaluateExpression(targetIdExpr, evaluationContext, String.class);
            String displayName =
                    !targetDisplayNameExpr.isEmpty()
                            ? evaluateExpression(
                                    targetDisplayNameExpr, evaluationContext, String.class)
                            : null;
            if (targetId != null) {
                context.setTarget(Target.of(targetType, targetId, displayName));
            }
        }

        String metadataExpr = auditedAction.metadata();
        if (!metadataExpr.isEmpty()) {
            Map<String, Object> metadata = parseMetadata(metadataExpr, evaluationContext);
            context.addAllMetadata(metadata);
        }

        String[] tags = auditedAction.tags();
        if (tags.length > 0) {
            context.addMetadata("tags", java.util.List.of(tags));
        }

        Object snapshotSource = null;
        if (auditedAction.captureChanges()
                && snapshotProvider != null
                && !auditedAction.snapshotSource().isEmpty()) {
            snapshotSource =
                    evaluateExpression(
                            auditedAction.snapshotSource(), evaluationContext, Object.class);
            if (snapshotSource != null) {
                context.setBeforeSnapshot(snapshotProvider.capture(snapshotSource));
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
            context.setOutcome(AuditOutcome.SUCCESS);
        } catch (Throwable t) {
            if (auditedAction.recordFailures()) {
                context.recordError(t);
            } else {
                throw t;
            }
            throw t;
        } finally {
            if (auditedAction.captureChanges()
                    && snapshotProvider != null
                    && snapshotSource != null) {
                context.setAfterSnapshot(snapshotProvider.capture(snapshotSource));
            }

            try {
                auditPipeline.process(context);
            } catch (Exception e) {
                // Don't let audit failures affect business logic
                // Log would go here in production
            }
        }

        return result;
    }

    private EvaluationContext createEvaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        for (int i = 0; i < args.length; i++) {
            context.setVariable("arg" + i, args[i]);
            context.setVariable("p" + i, args[i]);
        }

        return context;
    }

    private <T> T evaluateExpression(
            String expressionString, EvaluationContext context, Class<T> targetType) {
        if (expressionString == null || expressionString.isEmpty()) {
            return null;
        }
        try {
            Expression expression = expressionParser.parseExpression(expressionString);
            return expression.getValue(context, targetType);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseMetadata(String metadataExpr, EvaluationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        Matcher matcher = METADATA_PATTERN.matcher(metadataExpr);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String valueExpr = matcher.group(2).trim();
            Object value = evaluateExpression(valueExpr, context, Object.class);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }
}
