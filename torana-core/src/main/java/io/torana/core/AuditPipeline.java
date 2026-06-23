package io.torana.core;

import io.torana.api.model.AuditEntry;
import io.torana.spi.AuditErrorHandler;
import io.torana.spi.RedactionPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main audit processing pipeline.
 *
 * <p>This pipeline orchestrates the entire audit lifecycle:
 *
 * <ol>
 *   <li>Collect context from resolvers
 *   <li>Create the audit entry
 *   <li>Apply redaction
 *   <li>Persist the entry
 * </ol>
 *
 * <p>Error handling is configurable via {@code AuditErrorPolicy}:
 *
 * <ul>
 *   <li>{@code LOG_AND_CONTINUE} - Log errors and allow business operation to proceed (default)
 *   <li>{@code FAIL_TRANSACTION} - Throw exception to fail the business transaction
 *   <li>{@code CALLBACK} - Invoke custom {@code AuditErrorHandler} to decide
 * </ul>
 */
public class AuditPipeline {

    private static final Logger log = LoggerFactory.getLogger(AuditPipeline.class);

    private final ContextCollector contextCollector;
    private final AuditEntryFactory entryFactory;
    private final RedactionPolicy redactionPolicy;
    private final TransactionAwareWriter transactionAwareWriter;
    private final AuditErrorPolicy errorPolicy;
    private final AuditErrorHandler errorHandler;

    /**
     * Creates an audit pipeline with default error policy.
     *
     * @param contextCollector collects contextual information
     * @param entryFactory creates audit entries
     * @param redactionPolicy redacts sensitive data (may be null)
     * @param transactionAwareWriter writes entries with transaction awareness
     */
    public AuditPipeline(
            ContextCollector contextCollector,
            AuditEntryFactory entryFactory,
            RedactionPolicy redactionPolicy,
            TransactionAwareWriter transactionAwareWriter) {
        this(
                contextCollector,
                entryFactory,
                redactionPolicy,
                transactionAwareWriter,
                AuditErrorPolicy.LOG_AND_CONTINUE,
                null);
    }

    /**
     * Creates an audit pipeline with configurable error handling.
     *
     * @param contextCollector collects contextual information
     * @param entryFactory creates audit entries
     * @param redactionPolicy redacts sensitive data (may be null)
     * @param transactionAwareWriter writes entries with transaction awareness
     * @param errorPolicy determines how errors are handled
     * @param errorHandler optional custom error handler (used with CALLBACK policy)
     */
    public AuditPipeline(
            ContextCollector contextCollector,
            AuditEntryFactory entryFactory,
            RedactionPolicy redactionPolicy,
            TransactionAwareWriter transactionAwareWriter,
            AuditErrorPolicy errorPolicy,
            AuditErrorHandler errorHandler) {
        this.contextCollector = contextCollector;
        this.entryFactory = entryFactory;
        this.redactionPolicy = redactionPolicy;
        this.transactionAwareWriter = transactionAwareWriter;
        this.errorPolicy = errorPolicy;
        this.errorHandler = errorHandler;
    }

    /**
     * Processes an audit context through the pipeline.
     *
     * <p>Each phase is wrapped in error handling according to the configured {@code
     * AuditErrorPolicy}. If an error occurs:
     *
     * <ul>
     *   <li>{@code LOG_AND_CONTINUE}: Error is logged and processing continues
     *   <li>{@code FAIL_TRANSACTION}: Exception is thrown to fail the business operation
     *   <li>{@code CALLBACK}: Custom handler is invoked to decide
     * </ul>
     *
     * @param context the audit context to process
     */
    public void process(AuditContext context) {
        AuditEntry entry = null;

        try {
            contextCollector.collect(context);
            context.markCompleted();
        } catch (Exception e) {
            handleError(context, null, e, AuditErrorHandler.ErrorPhase.COLLECTION);
            return;
        }

        try {
            entry = entryFactory.create(context);
        } catch (Exception e) {
            handleError(context, null, e, AuditErrorHandler.ErrorPhase.CREATION);
            return;
        }

        try {
            entry = redactionPolicy != null ? redactionPolicy.apply(entry) : entry;
        } catch (Exception e) {
            handleError(context, entry, e, AuditErrorHandler.ErrorPhase.REDACTION);
            return;
        }

        try {
            transactionAwareWriter.write(entry, context.getOutcome());
        } catch (Exception e) {
            handleError(context, entry, e, AuditErrorHandler.ErrorPhase.PERSISTENCE);
        }
    }

    /**
     * Handles an error that occurred during audit processing.
     *
     * <p>The handling strategy is determined by the configured {@code AuditErrorPolicy}:
     *
     * <ul>
     *   <li>{@code LOG_AND_CONTINUE}: Logs the error at ERROR level
     *   <li>{@code FAIL_TRANSACTION}: Rethrows the exception (wrapped if checked)
     *   <li>{@code CALLBACK}: Invokes the custom error handler, falls back to logging if handler is
     *       null or throws an exception
     * </ul>
     *
     * @param context the audit context being processed (may be incomplete)
     * @param entry the audit entry (may be null if error occurred before creation)
     * @param error the exception that occurred
     * @param phase the phase where the error occurred
     */
    private void handleError(
            AuditContext context,
            AuditEntry entry,
            Exception error,
            AuditErrorHandler.ErrorPhase phase) {

        switch (errorPolicy) {
            case LOG_AND_CONTINUE -> logError(context, error, phase);

            case FAIL_TRANSACTION -> {
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new AuditProcessingException(
                        "Audit processing failed at phase " + phase, error);
            }

            case CALLBACK -> {
                if (errorHandler != null) {
                    try {
                        errorHandler.handleError(entry, error, phase);
                    } catch (Exception handlerError) {
                        log.error(
                                "Custom audit error handler threw exception while handling {} phase"
                                        + " error for action '{}'. Falling back to logging. Handler"
                                        + " error: {}",
                                phase,
                                getActionName(context),
                                handlerError.getMessage(),
                                handlerError);
                        logError(context, error, phase);
                    }
                } else {
                    log.warn(
                            "AuditErrorPolicy.CALLBACK configured but no AuditErrorHandler found. "
                                    + "Falling back to LOG_AND_CONTINUE behavior.");
                    logError(context, error, phase);
                }
            }
        }
    }

    /**
     * Logs an audit processing error at ERROR level.
     *
     * @param context the audit context
     * @param error the exception
     * @param phase the phase where the error occurred
     */
    private void logError(
            AuditContext context, Exception error, AuditErrorHandler.ErrorPhase phase) {
        log.error(
                "Audit processing failed at {} phase for action '{}': {}",
                phase,
                getActionName(context),
                error.getMessage(),
                error);
    }

    /**
     * Safely extracts the action name from the context.
     *
     * @param context the audit context
     * @return the action name or "unknown" if not available
     */
    private String getActionName(AuditContext context) {
        return context.getAction() != null ? context.getAction().name() : "unknown";
    }

    /**
     * Exception thrown when audit processing fails and {@code FAIL_TRANSACTION} policy is
     * configured.
     */
    public static class AuditProcessingException extends RuntimeException {
        public AuditProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
