package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.actions.GetPin;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.GetAuthenticationCertificate;
import com.jbraddockm.link.endpoint.pairing.PairWithDevice;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pimsync.PimFilters;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.account.CreatePimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.DeletePimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.FindPimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.ListPimAccounts;
import com.jbraddockm.link.endpoint.pimsync.account.SetDefaultCalendar;
import com.jbraddockm.link.endpoint.pimsync.account.UpdatePimAccount;
import com.jbraddockm.link.endpoint.pimsync.item.DeletePimItem;
import com.jbraddockm.link.endpoint.pimsync.item.GetPimItems;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.endpoint.update.InstallPackages;
import com.jbraddockm.link.endpoint.update.UninstallPackages;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link LinkMethod}: wire names, replay support, and whether a method works on a session
 * or on its own.
 */
@DisplayName("LinkMethod: the methods of the daemon")
class LinkMethodTest {

  private static Optional<DeviceCommand<?>> replay(LinkMethod method) {
    return method.replayCommand(null);
  }

  private static Optional<DeviceCommand<?>> replay(LinkMethod method, String argument) {
    return method.replayCommand(argument);
  }

  @Nested
  @DisplayName("the reachability guard")
  class ReachabilityGuard {

    @Test
    @DisplayName("refuses a session command over a Wi-Fi LAN address before it reaches the device")
    void refusesOverLan() {
      var scoped =
          (CommandSpec.SessionScoped<DeviceSession>)
              LinkMethod.GET_PIN.spec(_ -> {}, new Pairing(), new CertificateRetrieval());
      var session =
          DeviceSession.forTarget(
              new DeviceTarget(
                  "192.168.1.118", 8443, TransportScheme.HTTPS, new Authentication.NoPassword()));
      var output = new ByteArrayOutputStream();
      var reply = Reply.standalone(new PrintStream(output, true, StandardCharsets.UTF_8), 1L);

      scoped.factory().apply(new RequestParams(java.util.Map.of())).run(session, reply);

      var written = output.toString(StandardCharsets.UTF_8);
      assertThat(written).contains("COMMAND_FAILED").contains("getPin");
    }
  }

  @Nested
  @DisplayName("the names")
  class Names {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "GET_DEVICE_PROPERTIES, getDeviceProperties",
      "GET_DEVICE_CHARACTERISTICS, getDeviceCharacteristics",
      "GET_PIN, getPin",
      "GET_SMB_CREDENTIALS, getSmbCredentials",
      "LIST_PIM_ACCOUNTS, listPimAccounts",
      "FIND_PIM_ACCOUNT, findPimAccount",
      "GET_PIM_ITEMS, getPimItems",
      "CREATE_PIM_ACCOUNT, createPimAccount",
      "UPDATE_PIM_ACCOUNT, updatePimAccount",
      "DELETE_PIM_ACCOUNT, deletePimAccount",
      "SET_DEFAULT_CALENDAR, setDefaultCalendar",
      "DELETE_PIM_ITEM, deletePimItem",
      "PAIR_DEVICE, pairDevice",
      "GET_AUTHENTICATION_CERTIFICATE, getAuthenticationCertificate",
      "LIST_DEVICES, listDevices",
      "INSPECT_PACKAGE, inspectPackage",
      "INSTALL_PACKAGES, installPackages",
      "UNINSTALL_PACKAGES, uninstallPackages",
      "INSPECT_CERTIFICATE, inspectCertificate",
      "BACKUP_DEVICE, backupDevice",
      "RESTORE_DEVICE, restoreDevice"
    })
    @DisplayName("each method has the name that the protocol gives it, and is found by it")
    void eachMethodHasItsName(LinkMethod method, String name) {
      assertThat(method.wireName()).isEqualTo(name);
      assertThat(LinkMethod.byWireName(name)).contains(method);
    }

    @Test
    @DisplayName("are 23, and distinct")
    void areTwentyThreeAndDistinct() {
      assertThat(Arrays.stream(LinkMethod.values()).map(LinkMethod::wireName))
          .hasSize(23)
          .doesNotHaveDuplicates();
    }

    @ParameterizedTest(name = "\"{0}\" is not a method")
    @ValueSource(strings = {"", "getpin", "GetPin", "get_pin", "getPin ", "connect", "listDevice"})
    @DisplayName("a name that is not exactly one of them finds nothing")
    void anotherNameFindsNothing(String name) {
      assertThat(LinkMethod.byWireName(name)).isEmpty();
    }
  }

  @Nested
  @DisplayName("what can be replayed")
  class Replaying {

    @Test
    @DisplayName("the reads that take no argument give their command, and none with an argument")
    void theReadsThatTakeNoArgument() {
      assertThat(replay(LinkMethod.GET_DEVICE_PROPERTIES))
          .containsInstanceOf(GetDeviceProperties.class);
      assertThat(replay(LinkMethod.GET_PIN)).containsInstanceOf(GetPin.class);
      assertThat(replay(LinkMethod.LIST_PIM_ACCOUNTS)).containsInstanceOf(ListPimAccounts.class);
      assertThat(replay(LinkMethod.GET_DEVICE_PROPERTIES, "x")).isEmpty();
      assertThat(replay(LinkMethod.GET_PIN, "x")).isEmpty();
      assertThat(replay(LinkMethod.LIST_PIM_ACCOUNTS, "x")).isEmpty();
    }

    @Test
    @DisplayName("finding a PIM account is replayed with a workstation that is made up")
    void findingAnAccount() {
      assertThat(replay(LinkMethod.FIND_PIM_ACCOUNT)).contains(new FindPimAccount("replay"));
      assertThat(replay(LinkMethod.FIND_PIM_ACCOUNT, "x")).isEmpty();
      assertThat(replay(LinkMethod.CREATE_PIM_ACCOUNT))
          .contains(new CreatePimAccount("replay", "Replay"));
      assertThat(replay(LinkMethod.CREATE_PIM_ACCOUNT, "x")).isEmpty();
      assertThat(replay(LinkMethod.UPDATE_PIM_ACCOUNT))
          .contains(new UpdatePimAccount("replay", "Replay"));
      assertThat(replay(LinkMethod.DELETE_PIM_ACCOUNT)).contains(new DeletePimAccount("replay"));
      assertThat(replay(LinkMethod.SET_DEFAULT_CALENDAR))
          .contains(new SetDefaultCalendar("replay", "replay.object"));
      assertThat(replay(LinkMethod.SET_DEFAULT_CALENDAR, "x")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"contacts", "events", "calendars"})
    @DisplayName(
        "reading PIM items takes the service as its argument, with no filter, and has none without"
            + " it")
    void readingPimItems(String service) {
      assertThat(replay(LinkMethod.GET_PIM_ITEMS, service))
          .contains(new GetPimItems("replay", PimService.of(service), PimFilters.none()));
      assertThat(replay(LinkMethod.GET_PIM_ITEMS)).isEmpty();
      assertThat(replay(LinkMethod.DELETE_PIM_ITEM, service))
          .contains(new DeletePimItem("replay", PimService.of(service), "replay.object"));
      assertThat(replay(LinkMethod.DELETE_PIM_ITEM)).isEmpty();
    }

    @Test
    @DisplayName(
        "reading PIM items with a service that is not one is an invalid argument, naming service")
    void aServiceThatIsNotOne() {
      assertThatThrownBy(() -> replay(LinkMethod.GET_PIM_ITEMS, "tasks"))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("service"));
    }

    @Test
    @DisplayName(
        "pairing and asking for a certificate are replayed with values that are made up, and take"
            + " no argument")
    void pairingAndTheCertificate() {
      assertThat(replay(LinkMethod.PAIR_DEVICE)).containsInstanceOf(PairWithDevice.class);
      assertThat(replay(LinkMethod.GET_AUTHENTICATION_CERTIFICATE))
          .containsInstanceOf(GetAuthenticationCertificate.class);
      assertThat(replay(LinkMethod.PAIR_DEVICE, "x")).isEmpty();
      assertThat(replay(LinkMethod.GET_AUTHENTICATION_CERTIFICATE, "x")).isEmpty();
    }

    @Test
    @DisplayName(
        "an installation is replayed with one bar that is made up, an application, and no wait"
            + " between the polls")
    void anInstallation() {
      var command = (InstallPackages) replay(LinkMethod.INSTALL_PACKAGES).orElseThrow();

      assertThat(command.bars()).hasSize(1);
      assertThat(command.bars().getFirst().packageId()).isEqualTo("replay.package");
      assertThat(command.pollInterval()).isEqualTo(Duration.ZERO);
      assertThat(replay(LinkMethod.INSTALL_PACKAGES, "2")).isEmpty();
    }

    @Test
    @DisplayName(
        "a removal is replayed with one package unless a number is the argument, and is never"
            + " checked against the properties")
    void aRemoval() {
      var one = (UninstallPackages) replay(LinkMethod.UNINSTALL_PACKAGES).orElseThrow();

      assertThat(one.packageIds()).containsExactly("replay.package");
      assertThat(one.check()).isFalse();
    }

    @ParameterizedTest(name = "{0} packages")
    @ValueSource(strings = {"1", "2", "3", "9"})
    @DisplayName("a removal takes a number from 1 to 9 as its argument, and numbers the packages")
    void aRemovalOfSeveral(String count) {
      var command = (UninstallPackages) replay(LinkMethod.UNINSTALL_PACKAGES, count).orElseThrow();

      var number = Integer.parseInt(count);
      if (number == 1) {
        assertThat(command.packageIds()).containsExactly("replay.package");
      } else {
        assertThat(command.packageIds())
            .containsExactlyElementsOf(
                IntStream.rangeClosed(1, number)
                    .mapToObj(index -> "replay.package." + index)
                    .toList());
      }
    }

    @ParameterizedTest(name = "\"{0}\" is not a number of packages")
    @ValueSource(strings = {"0", "10", "x", "-1", "", "1.5", "01"})
    @DisplayName("a removal with any other argument has no command")
    void aRemovalWithAnotherArgument(String argument) {
      assertThat(replay(LinkMethod.UNINSTALL_PACKAGES, argument)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {"GET_SMB_CREDENTIALS", "LIST_DEVICES", "INSPECT_PACKAGE", "INSPECT_CERTIFICATE"})
    @DisplayName("the methods that do not talk to a device by a recording cannot be replayed")
    void notReplayable(String name) {
      var method = LinkMethod.valueOf(name);

      assertThat(replay(method)).isEmpty();
      assertThat(replay(method, "1")).isEmpty();
    }
  }

  @Nested
  @DisplayName("the specification of each method")
  class Specifying {

    private final List<CommandSpec<?>> specs =
        Arrays.stream(LinkMethod.values())
            .<CommandSpec<?>>map(
                method -> method.spec(_ -> {}, new Pairing(), new CertificateRetrieval()))
            .toList();

    @Test
    @DisplayName("has the name of its method")
    void hasTheNameOfItsMethod() {
      assertThat(specs)
          .extracting(CommandSpec::wireName)
          .containsExactlyElementsOf(
              Arrays.stream(LinkMethod.values()).map(LinkMethod::wireName).toList());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"LIST_DEVICES", "INSPECT_PACKAGE", "INSPECT_CERTIFICATE"})
    @DisplayName("works on its own, with no session, when it looks at a file or at the network")
    void worksOnItsOwn(String name) {
      assertThat(LinkMethod.valueOf(name).spec(_ -> {}, new Pairing(), new CertificateRetrieval()))
          .isInstanceOf(CommandSpec.Standalone.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "GET_DEVICE_PROPERTIES",
          "GET_PIN",
          "GET_SMB_CREDENTIALS",
          "LIST_PIM_ACCOUNTS",
          "FIND_PIM_ACCOUNT",
          "GET_PIM_ITEMS",
          "PAIR_DEVICE",
          "GET_AUTHENTICATION_CERTIFICATE",
          "INSTALL_PACKAGES",
          "UNINSTALL_PACKAGES"
        })
    @DisplayName("works on the session of a device when it talks to one")
    void worksOnASession(String name) {
      assertThat(LinkMethod.valueOf(name).spec(_ -> {}, new Pairing(), new CertificateRetrieval()))
          .isInstanceOf(CommandSpec.SessionScoped.class);
    }
  }
}
