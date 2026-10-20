package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.trace.TraceRecorder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for tracing a session: what is recorded for a login and a command, and what is redacted.
 */
@DisplayName("A traced session: what is kept of a login and a command, and what is hidden")
class TracedSessionTest {

  @TempDir private Path directory;
  private FakeDevice device;
  private String trace;

  @BeforeEach
  void runACommandWhileTracing() throws Exception {
    device = new FakeDevice("secret");
    device.propertiesXml = Resources.text(TracedSessionTest.class, "trace-session--properties.xml");
    var target =
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            TransportScheme.HTTP,
            new Authentication.Password("secret"));
    try (var session = DeviceSession.forTarget(target, new TraceRecorder(directory))) {
      succeeded(session.execute(new GetDeviceProperties(), CommandListener.SILENT));
    }
    try (var files = Files.list(directory)) {
      trace = files.sorted().map(TracedSessionTest::read).collect(Collectors.joining("\n=====\n"));
    }
  }

  @AfterEach
  void closeTheDevice() {
    device.close();
  }

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  @Test
  @DisplayName(
      "keeps the two requests of the login and the request of the command, each in a file of its"
          + " own")
  void keepsEveryExchange() {
    assertThat(trace.split("=====", -1)).hasSize(3);
  }

  @Test
  @DisplayName(
      "hides what identifies the device and shows that it is hidden, and keeps the other values,"
          + " and an empty value as empty")
  void hidesIdentifiers() {
    assertThat(trace).doesNotContain("REALSERIAL", "REALBBID", "359999999999999");
    assertThat(trace)
        .contains("<BoardSerialNumber>***</BoardSerialNumber>", "Mine", "<Meid></Meid>");
  }

  @Test
  @DisplayName(
      "hides the value of every cookie, the one issued before the login as well, and shows by name"
          + " that the cookie was sent")
  void hidesTheCookies() {
    assertThat(trace).doesNotContain("TOKEN-1", "PRELOGIN-TOKEN");
    assertThat(trace).contains(">>> Cookie: dtmauth=***", "dtmauth=***");
  }

  @Test
  @DisplayName(
      "hides the password of the file share and the hash of the password that answers the"
          + " challenge, and shows the masks")
  void hidesThePasswords() {
    assertThat(trace).doesNotContain(FakeDevice.SHARE_PASSWORD_PREFIX);
    assertThat(trace).contains("Password='***'", "challenge_data=***");
  }
}
