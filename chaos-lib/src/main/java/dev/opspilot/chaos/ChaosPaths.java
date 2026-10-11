package dev.opspilot.chaos;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Paths chaos must never touch: {@code /actuator/**} (probes and metrics must keep working)
 * and {@code /chaos/**} (we must always be able to reset).
 */
final class ChaosPaths {

    private ChaosPaths() {
    }

    static boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return isUnder(path, "/actuator") || isUnder(path, "/chaos");
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
