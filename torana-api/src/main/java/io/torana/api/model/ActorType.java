package io.torana.api.model;

/** The type of actor that caused an audited action. */
public enum ActorType {

    /** A human user interacting through a UI or API. */
    USER,

    /** A service account or API client. */
    SERVICE_ACCOUNT,

    /** The system itself (scheduled jobs, internal processes). */
    SYSTEM,

    /** An unauthenticated or unknown actor. */
    ANONYMOUS
}
