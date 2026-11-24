package com.los.model;

/**
 * Caller Security Context for Dynamic Role-Based Access Control.
 */
public class UserContext {

    private final String username;
    private final UserRole role;
    private final String token;

    public static final UserContext SYSTEM = new UserContext("SYSTEM", UserRole.ADMIN, null);
    public static final UserContext DEFAULT_OFFICER = new UserContext("officer_ramesh", UserRole.LOAN_OFFICER, null);

    public UserContext(String username, UserRole role) {
        this.username = (username != null && !username.trim().isEmpty()) ? username.trim() : "anonymous";
        this.role = (role != null) ? role : UserRole.LOAN_OFFICER;
        this.token = null;
    }

    public UserContext(String username, UserRole role, String token) {
        this.username = (username != null && !username.trim().isEmpty()) ? username.trim() : "anonymous";
        this.role = (role != null) ? role : UserRole.LOAN_OFFICER;
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }

    public boolean hasPermission(Permission permission) {
        return role != null && role.hasPermission(permission);
    }

    public void checkPermission(Permission permission) {
        if (!hasPermission(permission)) {
            throw new AccessDeniedException(role, permission);
        }
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "username='" + username + '\'' +
                ", role=" + role +
                '}';
    }
}
