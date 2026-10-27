package com.jbraddockm.link.transport.session;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.endpoint.actions.GetPin;
import com.jbraddockm.link.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceSession} replaying recorded answers without a network. */
@DisplayName("DeviceSession.replay: a command run against recorded answers")
class DeviceSessionReplayTest {

  private static String recording() {
    return Resources.text(DeviceSessionReplayTest.class, "device-session--pin.xml");
  }

  @Test
  @DisplayName("runs the command on the recorded answers, as it would on the device")
  void runsOnTheRecording() {
    var pin =
        succeeded(DeviceSession.replay(new GetPin(), List.of(recording()), CommandListener.SILENT));

    assertThat(pin.pin()).isNotBlank();
  }

  @Test
  @DisplayName(
      "fails with an error of input and output when the recording has no answer left for a request")
  void aRecordingThatRunsOut() {
    var failure = failed(DeviceSession.replay(new GetPin(), List.of(), CommandListener.SILENT));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(failure.error().message()).isEqualTo("the recording has no answer for this request");
  }

  @Test
  @DisplayName(
      "fails as a session that the device rejected when the recorded answer is the empty one of a"
          + " lost session")
  void aRecordedLostSession() {
    var failure = failed(DeviceSession.replay(new GetPin(), List.of(""), CommandListener.SILENT));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
    assertThat(failure.error().message())
        .isEqualTo("The device rejected the session right after a new login");
  }
}
