package com.los.model;

public class InvalidStateTransitionException extends RuntimeException {

    private final ApplicationStatus fromStatus;
    private final ApplicationStatus toStatus;

    public InvalidStateTransitionException(ApplicationStatus fromStatus, ApplicationStatus toStatus) {
        super(String.format("Invalid state transition from [%s] to [%s]", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public InvalidStateTransitionException(String message) {
        super(message);
        this.fromStatus = null;
        this.toStatus = null;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }
}
