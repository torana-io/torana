package io.torana.api.model;

/** The outcome of an audited business action. */
public enum AuditOutcome {

    /** The action completed successfully. */
    SUCCESS,

    /** The action failed due to an error or exception. */
    FAILURE,

    /** The action partially completed (some operations succeeded, others failed). */
    PARTIAL,

    /** The outcome could not be determined. */
    UNKNOWN
}
