package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link DeviceTarget}. */
@DisplayName("DeviceTarget: where a session goes")
class DeviceTargetTest {

  private static DeviceTarget target(String host, int port, TransportScheme scheme) {
    return new DeviceTarget(host, port, scheme, new Authentication.Password("secret"));
  }

  @Test
  @DisplayName("the usual port is 443")
  void theUsualPortIs443() {
    assertThat(DeviceTarget.DEFAULT_PORT).isEqualTo(443);
  }

  @Nested
  @DisplayName("url")
  class Url {

    @ParameterizedTest(name = "{0} {1} {2} is {4}")
    @CsvSource({
      "169.254.0.1, 443, HTTPS, /cgi-bin/login.cgi, https://169.254.0.1:443/cgi-bin/login.cgi",
      "169.254.0.1, 8443, HTTPS, /cgi-bin/login.cgi?request_version=3,"
          + " https://169.254.0.1:8443/cgi-bin/login.cgi?request_version=3",
      "device.local, 80, HTTP, /x, http://device.local:80/x",
      "::1, 443, HTTPS, /x, https://[::1]:443/x",
      "fe80::1, 443, HTTPS, /x, https://[fe80::1]:443/x",
      "fe80::1%en0, 443, HTTPS, /x, https://[fe80::1%en0]:443/x",
      "[::1], 443, HTTPS, /x, https://[::1]:443/x"
    })
    @DisplayName(
        "is the scheme, the host, the port and the path, with an address of IPv6 in brackets once")
    void isTheAddress(String host, int port, TransportScheme scheme, String path, String expected) {
      assertThat(target(host, port, scheme).url(path)).isEqualTo(expected);
    }
  }

  @Test
  @DisplayName(
      "keeps the zone of an IPv6 address as written, which is how the URI of the JDK reads it")
  void keepsTheZoneAsWritten() {
    var url = target("fe80::1%en0", 443, TransportScheme.HTTPS).url("/x");

    assertThat(URI.create(url).getHost()).isEqualTo("[fe80::1%en0]");
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusals {

    @ParameterizedTest(name = "host \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("a host that is missing or blank, naming host")
    void aBlankHost(String host) {
      assertThatThrownBy(() -> target(host, 443, TransportScheme.HTTPS))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("host"));
    }

    @ParameterizedTest(name = "port {0}")
    @ValueSource(ints = {0, -1, 65_536, 100_000})
    @DisplayName("a port outside 1 to 65535, naming port")
    void aPortOutsideTheRange(int port) {
      assertThatThrownBy(() -> target("169.254.0.1", port, TransportScheme.HTTPS))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("port"));
    }

    @ParameterizedTest(name = "port {0} is allowed")
    @ValueSource(ints = {1, 443, 65_535})
    @DisplayName("the ends of the range are allowed")
    void theEndsAreAllowed(int port) {
      assertThat(target("169.254.0.1", port, TransportScheme.HTTPS).port()).isEqualTo(port);
    }

    @Test
    @DisplayName("a scheme and an authentication are required")
    void aSchemeAndAnAuthenticationAreRequired() {
      assertThatNullPointerException()
          .isThrownBy(() -> new DeviceTarget("h", 443, null, new Authentication.Password("x")))
          .withMessage("scheme");
      assertThatNullPointerException()
          .isThrownBy(() -> new DeviceTarget("h", 443, TransportScheme.HTTPS, null))
          .withMessage("authentication");
    }
  }

  @Test
  @DisplayName(
      "toString says the host, the port, the scheme and the kind of authentication, and never the"
          + " password")
  void toStringHidesThePassword() {
    var text = target("169.254.0.1", 443, TransportScheme.HTTPS).toString();

    assertThat(text)
        .isEqualTo(
            "DeviceTarget[host=169.254.0.1, port=443, scheme=HTTPS, authentication=password]");
    assertThat(text).doesNotContain("secret");
  }
}
