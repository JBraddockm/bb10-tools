package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link FailureClassifier}: the error code and message for each way a connection or
 * login can fail.
 */
@DisplayName("FailureClassifier: what a caller is told when something goes wrong")
class FailureClassifierTest {

  static Stream<Arguments> connections() {
    return Stream.of(
        arguments(
            Named.of("a refused connection", new ConnectException("Connection refused")),
            CoreErrorCode.CONNECTION_FAILED,
            "refused"),
        arguments(
            Named.of(
                "a timeout while connecting", new ConnectTimeoutException("connect timed out")),
            CoreErrorCode.CONNECTION_FAILED,
            "timeout"),
        arguments(
            Named.of("a host that is not known", new UnknownHostException("device.invalid")),
            CoreErrorCode.CONNECTION_FAILED,
            "unknown_host"),
        arguments(
            Named.of("a failed handshake", new SSLException("handshake failed")),
            CoreErrorCode.CONNECTION_FAILED,
            "tls"),
        arguments(
            Named.of("a timeout while reading", new SocketTimeoutException("Read timed out")),
            CoreErrorCode.CONNECTION_LOST,
            "timeout"),
        arguments(
            Named.of("a reset", new SocketException("Connection reset")),
            CoreErrorCode.CONNECTION_LOST,
            "reset"),
        arguments(
            Named.of("no response", new NoHttpResponseException("the target failed to respond")),
            CoreErrorCode.CONNECTION_LOST,
            "reset"));
  }

  static Stream<Arguments> others() {
    return Stream.of(
        arguments(
            Named.of("a malformed URL", new MalformedURLException("no protocol")),
            CoreErrorCode.INVALID_ARGUMENT),
        arguments(
            Named.of("another failure of input and output", new IOException("disk full")),
            CoreErrorCode.IO_ERROR),
        arguments(
            Named.of("an argument that is not right", new IllegalArgumentException("bad value")),
            CoreErrorCode.INVALID_ARGUMENT));
  }

  @Test
  @DisplayName(
      "leavesSessionUsable is true for an argument that was not right, which nothing was sent for,"
          + " and false otherwise")
  void leavesSessionUsable() {
    assertThat(FailureClassifier.leavesSessionUsable(new IllegalArgumentException("x"))).isTrue();
    assertThat(FailureClassifier.leavesSessionUsable(new InvalidArgumentException("x", "y")))
        .isTrue();
    assertThat(FailureClassifier.leavesSessionUsable(new IOException("x"))).isFalse();
  }

  @Nested
  @DisplayName("the connection")
  class Connection {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.jbraddockm.link.transport.session.FailureClassifierTest#connections")
    @DisplayName("is failed or lost, with the cause, and the words of the exception")
    void aConnectionThatFailedOrWasLost(Throwable failure, ErrorCode code, String cause) {
      var error = FailureClassifier.classify(failure);

      assertThat(error.code()).isEqualTo(code);
      assertThat(error.details()).containsEntry("cause", cause);
      assertThat(error.message()).isEqualTo(failure.getMessage());
    }

    @Test
    @DisplayName(
        "that fails its handshake after a certificate was offered says that the device may have"
            + " refused it")
    void aHandshakeAfterACertificate() {
      var error = FailureClassifier.classify(new SSLException("bad_certificate"), true);

      assertThat(error.code()).isEqualTo(CoreErrorCode.CONNECTION_FAILED);
      assertThat(error.message())
          .startsWith("The TLS handshake failed while a client certificate was offered")
          .endsWith("(original message: bad_certificate)");
      assertThat(error.details()).containsEntry("cause", "tls").containsEntry("certificate", true);
    }

    @Test
    @DisplayName(
        "that fails its handshake over the tunnel without a certificate says the tunnel needs one")
    void aHandshakeOverTheTunnelWithoutACertificate() {
      var error =
          FailureClassifier.classify(new SSLException("no_cert"), false, ConnectionPath.TUNNEL);

      assertThat(error.code()).isEqualTo(CoreErrorCode.CONNECTION_FAILED);
      assertThat(error.message())
          .contains("tunnel connection needs the paired client certificate")
          .endsWith("(original message: no_cert)");
      assertThat(error.details()).containsEntry("cause", "tls");
    }

    @Test
    @DisplayName(
        "that is refused is not taken for a reset, though a refusal is a kind of socket exception")
    void aRefusalIsNotAReset() {
      assertThat(FailureClassifier.classify(new ConnectException("refused")).details())
          .containsEntry("cause", "refused");
    }

    @Test
    @DisplayName("is told the same for an exception with no message, by the name of the class")
    void anExceptionWithNoMessage() {
      assertThat(FailureClassifier.classify(new SocketException()).message())
          .isEqualTo("SocketException");
      assertThat(FailureClassifier.classify(new SocketException("   ")).message())
          .isEqualTo("SocketException");
    }

    @Test
    @DisplayName("is told without the blanks around the words of the exception")
    void blanksAreStripped() {
      assertThat(FailureClassifier.classify(new SocketException("  reset by peer \n")).message())
          .isEqualTo("reset by peer");
    }
  }

  @Nested
  @DisplayName("the login")
  class Login {

    @Test
    @DisplayName(
        "a password that was rejected gives the counts, and the number of attempts there were in"
            + " all")
    void aRejectedPassword() {
      var error = FailureClassifier.classify(new PasswordRejectedException(2, 8));

      assertThat(error.code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(error.details())
          .containsEntry("failedAttempts", 2)
          .containsEntry("retriesRemaining", 8)
          .containsEntry("maxAttempts", 10);
    }

    @Test
    @DisplayName(
        "a password that was rejected when no attempt remains is a locked device, with the failed"
            + " attempts")
    void aLockedDevice() {
      var error = FailureClassifier.classify(new PasswordRejectedException(10, 0));

      assertThat(error.code()).isEqualTo(CoreErrorCode.AUTH_LOCKED);
      assertThat(error.details())
          .containsOnlyKeys("failedAttempts")
          .containsEntry("failedAttempts", 10);
    }

    @Test
    @DisplayName(
        "an invalid password, a password that is missing, a clock out of sync and an answer that"
            + " cannot be used each have their own code")
    void theOtherLoginFailures() {
      assertThat(FailureClassifier.classify(new InvalidPasswordException()).code())
          .isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(FailureClassifier.classify(new PasswordRequiredException()).code())
          .isEqualTo(LinkErrorCode.PASSWORD_REQUIRED);
      assertThat(FailureClassifier.classify(new ClockOutOfSyncException()).code())
          .isEqualTo(CoreErrorCode.CLOCK_OUT_OF_SYNC);
      var unusable =
          FailureClassifier.classify(new UnusableLoginResponseException("Unknown status: Strange"));
      assertThat(unusable.code()).isEqualTo(CoreErrorCode.DEVICE_ERROR);
      assertThat(unusable.details()).containsEntry("extra", "Unknown status: Strange");
    }

    @Test
    @DisplayName("an invalid argument is passed on as the error that it already is")
    void anInvalidArgument() {
      var invalid = new InvalidArgumentException("port", "must be between 1 and 65535");

      assertThat(FailureClassifier.classify(invalid)).isEqualTo(invalid.toServiceError());
    }
  }

  @Nested
  @DisplayName("everything else")
  class Else {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.jbraddockm.link.transport.session.FailureClassifierTest#others")
    @DisplayName("is an error of the caller or of input and output, by what it is")
    void byWhatItIs(Throwable failure, ErrorCode code) {
      assertThat(FailureClassifier.classify(failure).code()).isEqualTo(code);
    }

    @Test
    @DisplayName("a fault that is not known is an internal error")
    void anInternalError() {
      assertThat(FailureClassifier.classify(new IllegalStateException("cannot happen")).code())
          .isEqualTo(CoreErrorCode.INTERNAL);
    }
  }

  @Nested
  @DisplayName("isStaleConnection")
  class Stale {

    @Test
    @DisplayName(
        "is true for a connection that was reset or that had no answer: the connection of the pool"
            + " was stale")
    void aResetIsStale() {
      assertThat(FailureClassifier.isStaleConnection(new SocketException("reset"))).isTrue();
      assertThat(FailureClassifier.isStaleConnection(new NoHttpResponseException("no answer")))
          .isTrue();
    }

    @Test
    @DisplayName(
        "is false for a connection that could not be made at all, and for what is not a failure of"
            + " a connection")
    void aRefusalIsNotStale() {
      assertThat(FailureClassifier.isStaleConnection(new ConnectException("refused"))).isFalse();
      assertThat(FailureClassifier.isStaleConnection(new ConnectTimeoutException("timeout")))
          .isFalse();
      assertThat(FailureClassifier.isStaleConnection(new UnknownHostException("x"))).isFalse();
      assertThat(FailureClassifier.isStaleConnection(new IOException("x"))).isFalse();
    }
  }
}
