package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PairWithDevice}: the request sent to {@code pairing.cgi}. */
@DisplayName("PairWithDevice: the request that pairs this computer with a device")
class PairWithDeviceTest {

  private static final String AUTHORITY =
      Resources.text(TestKeys.class, "certificate--device-authority-ec.pem");

  private static CgiRequest.Form requestOf(PairWithDevice command) {
    var step = command.conversation().start();
    assertThat(step).isInstanceOf(Step.Send.class);
    return (CgiRequest.Form) ((Step.Send<?>) step).request();
  }

  @Test
  @DisplayName(
      "is a form to pairing.cgi with the deviceid and the certdata, in this order, and nothing"
          + " else")
  void sendsTheTwoFields() {
    var form = requestOf(new PairWithDevice("01AB", AUTHORITY));

    assertThat(form.endpoint()).isEqualTo(Endpoint.PAIRING);
    assertThat(form.fields())
        .containsExactly(
            Map.entry(FormField.DEVICE_ID, "01AB"), Map.entry(FormField.CERT_DATA, AUTHORITY));
  }

  @Test
  @DisplayName("says what it does: pairing with the device")
  void describesItself() {
    assertThat(requestOf(new PairWithDevice("01AB", AUTHORITY)).describe())
        .isEqualTo("Pairing with the device");
  }

  @Test
  @DisplayName("writes the identifier in capitals, whatever case it was given in")
  void writesTheIdentifierInCapitals() {
    assertThat(new PairWithDevice("01ab", AUTHORITY).deviceId()).isEqualTo("01AB");
    assertThat(requestOf(new PairWithDevice(" 01ab ", AUTHORITY)).fields())
        .containsEntry(FormField.DEVICE_ID, "01AB");
  }

  @Test
  @DisplayName("changes the device, so it is not a read-only command and is never repeated")
  void changesTheDevice() {
    assertThat(new PairWithDevice("01AB", AUTHORITY).readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not an identifier")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "a b", "é"})
  @DisplayName(
      "refuses an identifier that is missing or not 1 to 64 letters, digits, dashes or underscores")
  void refusesABadIdentifier(String identifier) {
    assertThatThrownBy(() -> new PairWithDevice(identifier, AUTHORITY))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("deviceId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not an authority")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\n"})
  @DisplayName("refuses an authority that is missing or blank")
  void refusesABlankAuthority(String authority) {
    assertThatThrownBy(() -> new PairWithDevice("01AB", authority))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("authority"));
  }
}
