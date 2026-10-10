package com.jbraddockm.device;

import com.qnx.bbt.deploy.Deploy;
import com.jbraddockm.DeployError;
import com.jbraddockm.ErrorCode;
import com.jbraddockm.internal.Json;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

/** Maps the exceptions of the deploy tool to {@link DeployError errors} and decides what may be retried. */
final class FailureClassifier {

    private static final String ERROR_PREFIX = "Error: ";
    private static final String FAILURE_PREFIX = "result::failure";
    private static final Pattern DEVICE_CODE_AND_TEXT = Pattern.compile("^(\\d{1,4})\\s+(.*)$");

    private FailureClassifier() {
    }

    static DeployError classify(Throwable failure) {
        return switch (failure) {
            case InvalidArgumentException invalid -> invalid.toDeployError();
            case Deploy.LoginAuthException auth when auth.getRemaining() == 0 -> new DeployError(
                    ErrorCode.AUTH_LOCKED, describe(auth), Json.object("failedAttempts", auth.getFailedAttempts()));
            case Deploy.LoginAuthException auth -> new DeployError(ErrorCode.AUTH_FAILED, describe(auth), Json.object(
                    "failedAttempts", auth.getFailedAttempts(),
                    "retriesRemaining", auth.getRemaining(),
                    "maxAttempts", auth.getFailedAttempts() + auth.getRemaining()));
            case Deploy.LoginSystemException system -> new DeployError(
                    ErrorCode.DEVICE_ERROR, describe(system), Json.object("extra", system.getExtra()));
            case Deploy.LoginException login -> new DeployError(
                    describe(login).toLowerCase(Locale.ROOT).contains("clock")
                            ? ErrorCode.CLOCK_OUT_OF_SYNC // stop-gap: both cases are a plain LoginException
                            : ErrorCode.AUTH_FAILED,
                    describe(login));
            case Deploy.DeployException deploy -> classifyToolReport(deploy);
            case ConnectException _ -> connection(ErrorCode.CONNECTION_FAILED, "refused", failure); // includes HttpHostConnectException
            case ConnectTimeoutException _ -> connection(ErrorCode.CONNECTION_FAILED, "timeout", failure);
            case UnknownHostException _ -> connection(ErrorCode.CONNECTION_FAILED, "unknown_host", failure);
            case SocketTimeoutException _ -> connection(ErrorCode.CONNECTION_LOST, "timeout", failure);
            case SocketException _, NoHttpResponseException _ -> connection(ErrorCode.CONNECTION_LOST, "reset", failure);
            case MalformedURLException _ -> new DeployError(ErrorCode.INVALID_ARGUMENT, describe(failure));
            case IOException _ -> new DeployError(ErrorCode.IO_ERROR, describe(failure));
            case IllegalArgumentException _ -> new DeployError(ErrorCode.INVALID_ARGUMENT, describe(failure));
            default -> DeployError.internal(failure);
        };
    }

    /** Tells whether a failure means that the device has forgotten the login (the cookie expired). */
    static boolean isStaleAuthentication(Throwable failure) {
        return failure instanceof Deploy.DeployException deploy && deploy.isAuthenticationFailure();
    }

    /**
     * Tells whether a failure looks like a dead pooled connection, which is worth one retry, as opposed to
     * a device that cannot be reached at all.
     */
    static boolean isStaleConnection(Throwable failure) {
        return switch (failure) {
            case ConnectTimeoutException _, ConnectException _, UnknownHostException _ -> false; // the device is unreachable, not a stale connection
            case SocketException _, NoHttpResponseException _ -> true;
            default -> false;
        };
    }

    /**
     * Tells whether the session stays usable after a failure. A clean refusal by the device or a bad argument
     * does not damage it; anything else may have left a half-initialised connection behind.
     */
    static boolean leavesSessionUsable(Throwable failure) {
        return switch (failure) {
            case Deploy.DeployException deploy -> !deploy.isAuthenticationFailure();
            case IllegalArgumentException _ -> true;
            default -> false;
        };
    }

    /**
     * A message the tool printed and then raised as an exception, for example
     * {@code Error: Device is not in the Development Mode...} or
     * {@code result::failure 810 cannot access member}.
     */
    private static DeployError classifyToolReport(Deploy.DeployException exception) {
        var line = describe(exception);
        var details = Json.object("line", line);
        var message = messageOf(line, details);
        var code = exception.isAuthenticationFailure() ? ErrorCode.AUTH_FAILED
                : message.toLowerCase(Locale.ROOT).contains("not in the development mode") ? ErrorCode.DEVMODE_DISABLED
                : ErrorCode.COMMAND_FAILED;
        return new DeployError(code, message, details);
    }

    /** Strips the marker from a reported line and, for {@code result::failure <code> <text>}, records the code. */
    private static String messageOf(String line, Map<String, Object> details) {
        if (line.startsWith(ERROR_PREFIX)) {
            return line.substring(ERROR_PREFIX.length());
        }
        if (!line.startsWith(FAILURE_PREFIX)) {
            return line;
        }
        var remainder = line.substring(FAILURE_PREFIX.length()).strip();
        var matcher = DEVICE_CODE_AND_TEXT.matcher(remainder);
        if (matcher.matches()) {
            // The number is the device's own status code; its meaning is not identified (500 and 810 seen).
            details.put("deviceCode", Integer.valueOf(matcher.group(1)));
            return matcher.group(2);
        }
        return remainder.isEmpty() ? "The device reported a failure" : remainder;
    }

    private static DeployError connection(ErrorCode code, String cause, Throwable failure) {
        return new DeployError(code, describe(failure), Json.object("cause", cause));
    }

    private static String describe(Throwable failure) {
        var message = failure.getLocalizedMessage();
        // The tool's own bundle text can end in a space ("File does not exist ... cannot read: <path> ").
        return message != null && !message.isBlank() ? message.strip() : failure.getClass().getSimpleName();
    }
}
