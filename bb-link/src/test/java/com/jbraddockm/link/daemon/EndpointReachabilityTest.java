package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.transport.session.ConnectionPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link EndpointReachability}: which methods a connection path may run. */
@DisplayName("EndpointReachability: refusing a method a path cannot reach")
class EndpointReachabilityTest {

  @Nested
  @DisplayName("over USB and the tunnel")
  class FullSurface {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkMethod.class)
    @DisplayName("every method is allowed over USB")
    void everyMethodOverUsb(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.USB)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkMethod.class)
    @DisplayName("every method is allowed over the tunnel")
    void everyMethodOverTunnel(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.TUNNEL)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkMethod.class)
    @DisplayName("every method is allowed over an address of an unknown shape")
    void everyMethodOverOther(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.OTHER)).isEmpty();
    }
  }

  @Nested
  @DisplayName("over a Wi-Fi LAN address")
  class LanSurface {

    @Test
    @DisplayName("getSmbCredentials is allowed: it needs only the login the session already did")
    void smbCredentialsAllowed() {
      assertThat(
              EndpointReachability.refusalFor(LinkMethod.GET_SMB_CREDENTIALS, ConnectionPath.LAN))
          .isEmpty();
    }

    @Test
    @DisplayName("getAuthenticationCertificate is allowed: it works through the login")
    void certificateAllowed() {
      assertThat(
              EndpointReachability.refusalFor(
                  LinkMethod.GET_AUTHENTICATION_CERTIFICATE, ConnectionPath.LAN))
          .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = LinkMethod.class,
        names = {
          "GET_DEVICE_PROPERTIES",
          "GET_PIN",
          "LIST_PIM_ACCOUNTS",
          "FIND_PIM_ACCOUNT",
          "GET_PIM_ITEMS",
          "PAIR_DEVICE",
          "INSTALL_PACKAGES",
          "UNINSTALL_PACKAGES"
        })
    @DisplayName("a method that needs more than the login is refused, as COMMAND_FAILED")
    void methodNeedingMoreIsRefused(LinkMethod method) {
      var refusal = EndpointReachability.refusalFor(method, ConnectionPath.LAN);

      assertThat(refusal).isPresent();
      assertThat(refusal.orElseThrow().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
    }

    @Test
    @DisplayName("the refusal names the method and the paths that would reach it")
    void refusalNamesTheMethodAndThePaths() {
      var refusal =
          EndpointReachability.refusalFor(LinkMethod.INSTALL_PACKAGES, ConnectionPath.LAN)
              .orElseThrow();

      assertThat(refusal.message())
          .contains("installPackages")
          .contains("Wi-Fi LAN")
          .contains("USB")
          .contains("tunnel");
    }
  }
}
