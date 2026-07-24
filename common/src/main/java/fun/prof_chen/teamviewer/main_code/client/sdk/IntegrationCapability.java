package fun.prof_chen.teamviewer.main_code.client.sdk;

import java.util.Objects;

/** Machine-readable support report for one optional adapter port. */
public record IntegrationCapability(
        String id,
        String role,
        IntegrationSupportStatus status,
        String detail) {
    public IntegrationCapability {
        id = Objects.requireNonNull(id, "id");
        role = Objects.requireNonNull(role, "role");
        status = Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
    }
}
