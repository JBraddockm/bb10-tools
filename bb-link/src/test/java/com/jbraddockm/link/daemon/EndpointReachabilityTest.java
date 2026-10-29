package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link EndpointReachability}: which methods a connection path may run. */
@DisplayName("EndpointReachability: refusing a method a path cannot reach")
class EndpointReachabilityTest {

  // wipe.cgi and pimsync.cgi do not answer over the tunnel, so the PIN read and every PIM method
  // are served only over USB; the tunnel reaches everything else. The names are listed inline in
  // each @EnumSource below (an annotation needs a literal array).

  @Nested
  @DisplayName("over USB")
  class UsbSurface {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkMethod.class)
    @DisplayName("every method is allowed over USB")
    void everyMethodOverUsb(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.USB)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LinkMethod.class)
    @DisplayName("every method is allowed over an address of an unknown shape")
    void everyMethodOverOther(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.OTHER)).isEmpty();
    }
  }

  @Nested
  @DisplayName("over the Wi-Fi tunnel")
  class TunnelSurface {

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = LinkMethod.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {
          "GET_PIN",
          "LIST_PIM_ACCOUNTS",
          "FIND_PIM_ACCOUNT",
          "GET_PIM_ITEMS",
          "CREATE_PIM_ACCOUNT",
          "UPDATE_PIM_ACCOUNT",
          "DELETE_PIM_ACCOUNT",
          "SET_DEFAULT_CALENDAR",
          "DELETE_PIM_ITEM",
          "PUT_PIM_ITEM",
          "SUMMARIZE_PIM_SYNC",
          "FINISH_PIM_SYNC",
          "GET_PIM_PHOTO",
          "PUT_PIM_PHOTO"
        })
    @DisplayName(
        "a method whose program the tunnel serves is allowed (backup, install, pairing, …)")
    void tunnelServedMethodsAllowed(LinkMethod method) {
      assertThat(EndpointReachability.refusalFor(method, ConnectionPath.TUNNEL)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = LinkMethod.class,
        mode = EnumSource.Mode.INCLUDE,
        names = {
          "GET_PIN",
          "LIST_PIM_ACCOUNTS",
          "FIND_PIM_ACCOUNT",
          "GET_PIM_ITEMS",
          "CREATE_PIM_ACCOUNT",
          "UPDATE_PIM_ACCOUNT",
          "DELETE_PIM_ACCOUNT",
          "SET_DEFAULT_CALENDAR",
          "DELETE_PIM_ITEM",
          "PUT_PIM_ITEM",
          "SUMMARIZE_PIM_SYNC",
          "FINISH_PIM_SYNC",
          "GET_PIM_PHOTO",
          "PUT_PIM_PHOTO"
        })
    @DisplayName(
        "the PIN read and every PIM method are refused: their programs do not answer there")
    void usbOnlyMethodsRefusedOverTunnel(LinkMethod method) {
      var refusal = EndpointReachability.refusalFor(method, ConnectionPath.TUNNEL);

      assertThat(refusal).isPresent();
      assertThat(refusal.orElseThrow().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(refusal.orElseThrow().message()).contains("Wi-Fi tunnel").contains("USB");
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
