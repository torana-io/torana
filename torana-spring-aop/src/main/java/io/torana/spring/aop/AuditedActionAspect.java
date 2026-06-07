package io.torana.spring.aop;

import io.torana.api.AuditedAction;
import io.torana.api.AuditedCreate;
import io.torana.api.AuditedDelete;
import io.torana.api.AuditedUpdate;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Aspect that intercepts methods annotated with {@link AuditedAction} and preset annotations.
 *
 * <p>This aspect processes the following annotations:
 *
 * <ul>
 *   <li>{@link AuditedAction} - General purpose audit annotation
 *   <li>{@link AuditedCreate} - Preset for entity creation (action: "entity.created")
 *   <li>{@link AuditedUpdate} - Preset for entity updates (action: "entity.updated")
 *   <li>{@link AuditedDelete} - Preset for entity deletion (action: "entity.deleted")
 * </ul>
 *
 * <p>Processing steps:
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

    private static final Logger log = LoggerFactory.getLogger(AuditedActionAspect.class);

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
        return processAudit(
                joinPoint,
                auditedAction.value(),
                auditedAction.targetType(),
                auditedAction.targetId(),
                auditedAction.targetDisplayName(),
                auditedAction.captureChanges(),
                auditedAction.snapshotSource(),
                auditedAction.metadataFields(),
                auditedAction.metadataFields(),
                auditedAction.tags(),
                auditedAction.recordFailures());
    }

    /**
     * Advice for {@code @AuditedCreate} preset annotation.
     *
     * <p>Processes methods annotated with {@code @AuditedCreate} by extracting the preset's
     * attributes and delegating to the core audit processing logic.
     *
     * @param joinPoint the join point
     * @param auditedCreate the {@code @AuditedCreate} annotation
     * @return the method result
     * @throws Throwable if the method throws
     */
    @Around("@annotation(auditedCreate)")
    public Object auditCreate(ProceedingJoinPoint joinPoint, AuditedCreate auditedCreate)
            throws Throwable {
        AuditedAction baseAction = AuditedCreate.class.getAnnotation(AuditedAction.class);
        String actionName = baseAction.value();

        return processAudit(
                joinPoint,
                actionName,
                auditedCreate.targetType(),
                auditedCreate.targetId(),
                auditedCreate.targetDisplayName(),
                auditedCreate.captureChanges(),
                auditedCreate.snapshotSource(),
                auditedCreate.metadataFields(),
                new String[0],
                auditedCreate.tags(),
                auditedCreate.recordFailures());
    }

    /**
     * Advice for {@code @AuditedUpdate} preset annotation.
     *
     * <p>Processes methods annotated with {@code @AuditedUpdate} by extracting the preset's
     * attributes and delegating to the core audit processing logic.
     *
     * @param joinPoint the join point
     * @param auditedUpdate the {@code @AuditedUpdate} annotation
     * @return the method result
     * @throws Throwable if the method throws
     */
    @Around("@annotation(auditedUpdate)")
    public Object auditUpdate(ProceedingJoinPoint joinPoint, AuditedUpdate auditedUpdate)
            throws Throwable {
        AuditedAction baseAction = AuditedUpdate.class.getAnnotation(AuditedAction.class);
        String actionName = baseAction.value();

        return processAudit(
                joinPoint,
                actionName,
                auditedUpdate.targetType(),
                auditedUpdate.targetId(),
                auditedUpdate.targetDisplayName(),
                auditedUpdate.captureChanges(),
                auditedUpdate.snapshotSource(),
                auditedUpdate.metadataFields(),
                new String[0],
                auditedUpdate.tags(),
                auditedUpdate.recordFailures());
    }

    /**
     * Advice for {@code @AuditedDelete} preset annotation.
     *
     * <p>Processes methods annotated with {@code @AuditedDelete} by extracting the preset's
     * attributes and delegating to the core audit processing logic.
     *
     * @param joinPoint the join point
     * @param auditedDelete the {@code @AuditedDelete} annotation
     * @return the method result
     * @throws Throwable if the method throws
     */
    @Around("@annotation(auditedDelete)")
    public Object auditDelete(ProceedingJoinPoint joinPoint, AuditedDelete auditedDelete)
            throws Throwable {
        AuditedAction baseAction = AuditedDelete.class.getAnnotation(AuditedAction.class);
        String actionName = baseAction.value();

        return processAudit(
                joinPoint,
                actionName,
                auditedDelete.targetType(),
                auditedDelete.targetId(),
                auditedDelete.targetDisplayName(),
                auditedDelete.captureChanges(),
                auditedDelete.snapshotSource(),
                auditedDelete.metadataFields(),
                new String[0],
                auditedDelete.tags(),
                auditedDelete.recordFailures());
    }

    /**
     * Core audit processing logic extracted for reuse by preset annotations.
     *
     * <p>This method contains the shared audit processing logic used by both {@link AuditedAction}
     * and preset annotations ({@link AuditedCreate}, {@link AuditedUpdate}, {@link AuditedDelete}).
     *
     * @param joinPoint the join point
     * @param actionName the action name
     * @param targetType the target type
     * @param targetId the target ID expression
     * @param targetDisplayName the target display name expression
     * @param captureChanges whether to capture changes
     * @param snapshotSource the snapshot source expression
     * @param metadataFields the metadata fields array
     * @param metadataString the legacy metadata string
     * @param tags the tags
     * @param recordFailures whether to record failures
     * @return the method result
     * @throws Throwable if the method throws
     */
    private Object processAudit(
            ProceedingJoinPoint joinPoint,
            String actionName,
            String targetType,
            String targetId,
            String targetDisplayName,
            boolean captureChanges,
            String snapshotSource,
            String[] metadataFields,
            String[] metadataString,
            String[] tags,
            boolean recordFailures)
            throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        EvaluationContext evaluationContext = createEvaluationContext(method, args);

        AuditContext context = new AuditContext();
        context.markStarted();
        context.setAction(AuditAction.of(actionName));

        String targetIdExpr = targetId;
        String targetDisplayNameExpr = targetDisplayName;

        if (!targetType.isEmpty() && !targetIdExpr.isEmpty()) {
            String evaluatedTargetId =
                    evaluateExpression(targetIdExpr, evaluationContext, String.class);
            String displayName =
                    !targetDisplayNameExpr.isEmpty()
                            ? evaluateExpression(
                                    targetDisplayNameExpr, evaluationContext, String.class)
                            : null;
            if (evaluatedTargetId != null) {
                context.setTarget(Target.of(targetType, evaluatedTargetId, displayName));
            }
        }

        io.torana.api.AuditMetadata auditMetadata =
                method.getAnnotation(io.torana.api.AuditMetadata.class);
        if (auditMetadata != null) {
            Map<String, Object> metadata = parseAuditMetadata(auditMetadata, evaluationContext);
            context.addAllMetadata(metadata);
        }

        if (metadataFields.length > 0) {
            Map<String, Object> metadata = parseMetadataFields(metadataFields, evaluationContext);
            context.addAllMetadata(metadata);
        }

        // Process legacy metadata string (deprecated but supported for backward compatibility)
        // Note: Presets don't support the legacy format
        if (metadataString.length > 0 && !metadataString[0].isEmpty()) {
            Map<String, Object> metadata = parseMetadata(metadataString[0], evaluationContext);
            context.addAllMetadata(metadata);
        }

        if (tags.length > 0) {
            context.addMetadata("tags", java.util.List.of(tags));
        }

        Object snapshotSourceObj = null;
        if (captureChanges && snapshotProvider != null && !snapshotSource.isEmpty()) {
            snapshotSourceObj = evaluateExpression(snapshotSource, evaluationContext, Object.class);
            if (snapshotSourceObj != null) {
                context.setBeforeSnapshot(snapshotProvider.capture(snapshotSourceObj));
            }
        }

        Object result;
        try {
            result = joinPoint.proceed();
            context.setOutcome(AuditOutcome.SUCCESS);
        } catch (Throwable t) {
            if (recordFailures) {
                context.recordError(t);
            } else {
                throw t;
            }
            throw t;
        } finally {
            if (captureChanges && snapshotProvider != null && snapshotSourceObj != null) {
                context.setAfterSnapshot(snapshotProvider.capture(snapshotSourceObj));
            }

            // Pipeline handles errors internally based on configured AuditErrorPolicy
            auditPipeline.process(context);
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

    /**
     * Parses metadata from key:expression string array.
     *
     * <p>This is the recommended metadata format, replacing the legacy comma-separated {@code
     * metadata} string. Each array element is parsed independently, allowing complex SpEL
     * expressions with commas, quotes, and method calls without parsing issues.
     *
     * <p>Expected format for each element: {@code "key:expression"} where the expression is
     * evaluated as a SpEL expression against method parameters.
     *
     * @param fields array of "key:expression" strings
     * @param context SpEL evaluation context
     * @return map of evaluated metadata (null values are excluded)
     */
    private Map<String, Object> parseMetadataFields(String[] fields, EvaluationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        for (String field : fields) {
            int colonIndex = field.indexOf(':');
            if (colonIndex > 0 && colonIndex < field.length() - 1) {
                String key = field.substring(0, colonIndex).trim();
                String valueExpr = field.substring(colonIndex + 1).trim();
                Object value = evaluateExpression(valueExpr, context, Object.class);
                if (value != null) {
                    metadata.put(key, value);
                }
            } else {
                log.warn(
                        "Invalid metadata field format: '{}'. Expected 'key:expression'. Skipping"
                                + " this field.",
                        field);
            }
        }
        return metadata;
    }

    /**
     * Parses metadata from {@code @AuditMetadata} annotation.
     *
     * <p>This is the type-safe metadata format providing better IDE support and compile-time
     * validation through explicit {@code @MetadataField} structure.
     *
     * <p>Each {@link io.torana.api.MetadataField} defines a key and SpEL expression. Expressions
     * are evaluated against method parameters, and null results are excluded from the returned map.
     *
     * @param auditMetadata the {@code @AuditMetadata} annotation instance
     * @param context SpEL evaluation context
     * @return map of evaluated metadata (null values are excluded)
     */
    private Map<String, Object> parseAuditMetadata(
            io.torana.api.AuditMetadata auditMetadata, EvaluationContext context) {
        Map<String, Object> metadata = new HashMap<>();
        for (io.torana.api.MetadataField field : auditMetadata.value()) {
            String key = field.key();
            String valueExpr = field.value();
            Object value = evaluateExpression(valueExpr, context, Object.class);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        return metadata;
    }
}
