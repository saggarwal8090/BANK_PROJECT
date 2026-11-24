package com.los.model;

/**
 * Thrown when an authenticated user attempts an action forbidden by their assigned UserRole.
 */
public class AccessDeniedException extends RuntimeException {

    private final UserRole userRole;
    private final Permission requiredPermission;

    public AccessDeniedException(String message) {
        super(message);
        this.userRole = null;
        this.requiredPermission = null;
    }

    public AccessDeniedException(UserRole userRole, Permission requiredPermission) {
        super(String.format("Access Denied: Role [%s] lacks required permission [%s]", userRole, requiredPermission));
        this.userRole = userRole;
        this.requiredPermission = requiredPermission;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
