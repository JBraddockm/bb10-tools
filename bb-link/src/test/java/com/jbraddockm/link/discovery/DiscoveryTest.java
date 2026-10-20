package com.jbraddockm.link.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for device discovery: {@link ConnectionKind}, {@link DiscoveredDevice}, {@link DeviceList},
 * {@link DeviceDiscovery} and {@link LibraryDeviceScanner}.
 */
@DisplayName("Discovery: finding devices")
class DiscoveryTest {

  private static final DiscoveredDevice WIFI =
      new DiscoveredDevice("blackberry-0000.lan", "192.168.1.82", ConnectionKind.WIFI, null);
  private static final DiscoveredDevice USB =
      new DiscoveredDevice(
          "BlackBerry 10 10.3.3.2205", "169.254.0.1", ConnectionKind.USB, "10.3.3.2205");

  @Nested
  @DisplayName("ConnectionKind")
  class Kind {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"WIFI, WiFi", "USB, USB", "SIMULATOR, Simulator"})
    @DisplayName(
        "each kind has the spelling that the protocol uses, which is also the word of the library,"
            + " and is found by it")
    void eachKindHasItsSpelling(ConnectionKind kind, String spelling) {
      assertThat(kind.wireName()).isEqualTo(spelling);
      assertThat(ConnectionKind.fromDetectorText(spelling)).isEqualTo(kind);
    }

    @ParameterizedTest(name = "\"{0}\" is not a kind")
    @ValueSource(strings = {"", "wifi", "WIFI", "Wi-Fi", "usb", "Bluetooth", " USB"})
    @DisplayName("a word of the library that is not exactly one of them is refused, saying which")
    void aWordThatIsNotAKind(String text) {
      assertThatThrownBy(() -> ConnectionKind.fromDetectorText(text))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: " + text);
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("no word at all is refused as well")
    void noWord(String text) {
      assertThatThrownBy(() -> ConnectionKind.fromDetectorText(text))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: null");
    }
  }

  @Nested
  @DisplayName("DiscoveredDevice and DeviceList")
  class Results {

    @Test
    @DisplayName(
        "a device is said by its host name, its address, the spelling of its connection and its"
            + " version")
    void aDeviceAsJson() {
      assertThat(USB.toJson())
          .containsOnlyKeys("hostname", "address", "connection", "version")
          .containsEntry("connection", "USB")
          .containsEntry("version", "10.3.3.2205");
      assertThat(List.copyOf(USB.toJson().keySet()))
          .containsExactly("hostname", "address", "connection", "version");
    }

    @Test
    @DisplayName("a device whose version is not known is said without it on the wire")
    void aDeviceWithoutAVersion() {
      assertThat(Json.write(WIFI.toJson()))
          .doesNotContain("version")
          .contains("\"connection\":\"WiFi\"");
    }

    @Test
    @DisplayName(
        "a list is said as the devices, in order, and keeps its own copy of what it was given")
    void aListAsJson() {
      var given = new ArrayList<>(List.of(WIFI, USB));

      var list = new DeviceList(given);
      given.clear();

      assertThat(list.devices()).containsExactly(WIFI, USB);
      assertThat(list.toJson())
          .containsOnlyKeys("devices")
          .containsEntry("devices", List.of(WIFI.toJson(), USB.toJson()));
      assertThatThrownBy(() -> list.devices().add(WIFI))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("DeviceDiscovery")
  class Discovering {

    @Test
    @DisplayName(
        "tells each device as it is found, in the order that it was found, and gives them all as"
            + " the result")
    void tellsEachDeviceAndGivesAll() {
      DeviceScanner scanner = List.of(WIFI, USB)::forEach;
      var told = new ArrayList<DiscoveredDevice>();

      var list = DeviceDiscovery.discover(scanner, told::add);

      assertThat(told).containsExactly(WIFI, USB);
      assertThat(list.devices()).containsExactly(WIFI, USB);
    }

    @Test
    @DisplayName("finds nothing when the scanner finds nothing: an empty list, and nobody told")
    void findsNothing() {
      var told = new ArrayList<DiscoveredDevice>();

      var list = DeviceDiscovery.discover(_ -> {}, told::add);

      assertThat(list.devices()).isEmpty();
      assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("takes devices that are found from many threads at once, without losing one")
    void takesDevicesFromManyThreads() {
      var names = IntStream.range(0, 400).mapToObj(index -> "device-" + index).toList();
      DeviceScanner scanner =
          found -> {
            try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
              names.forEach(
                  name ->
                      pool.submit(
                          () ->
                              found.accept(
                                  new DiscoveredDevice(
                                      name, "10.0.0.1", ConnectionKind.WIFI, null))));
              pool.shutdown();
              assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException _) {
              Thread.currentThread().interrupt();
            }
          };
      var told = Collections.synchronizedList(new ArrayList<DiscoveredDevice>());

      var list = DeviceDiscovery.discover(scanner, told::add);

      assertThat(list.devices())
          .extracting(DiscoveredDevice::hostname)
          .containsExactlyInAnyOrderElementsOf(names);
      assertThat(told).hasSize(400);
    }

    @Test
    @DisplayName(
        "uses the library when it is asked for the usual scanner, which finds nothing until it is"
            + " run")
    void theUsualScannerIsTheLibraryOne() {
      assertThat(DeviceScanner.library()).isInstanceOf(LibraryDeviceScanner.class);
    }
  }

  @Nested
  @DisplayName("LibraryDeviceScanner: what the library says becomes what the daemon says")
  class Converting {

    @Test
    @DisplayName(
        "a device is the host name and the address that the library gave, with the kind that its"
            + " word names")
    void aDevice() {
      assertThat(
              LibraryDeviceScanner.convert(
                  "blackberry-0000.lan", "192.168.1.82", "WiFi", "10.3.3.2205"))
          .isEqualTo(
              new DiscoveredDevice(
                  "blackberry-0000.lan", "192.168.1.82", ConnectionKind.WIFI, "10.3.3.2205"));
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @CsvSource(value = {"'  10.3.3.2205  ', 10.3.3.2205", "10.3.3.2205, 10.3.3.2205"})
    @DisplayName("a version is kept without the blanks around it")
    void aVersionIsStripped(String given, String expected) {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", given).version())
          .isEqualTo(expected);
    }

    @ParameterizedTest(name = "version \"{0}\"")
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("a version that is empty or blank is no version")
    void aBlankVersion(String given) {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", given).version()).isNull();
    }

    @Test
    @DisplayName("a version that the library did not give is no version")
    void noVersion() {
      assertThat(LibraryDeviceScanner.convert("h", "a", "USB", null).version()).isNull();
    }

    @Test
    @DisplayName("a kind of connection that is not known is refused: the daemon does not guess")
    void anUnknownKind() {
      assertThatThrownBy(() -> LibraryDeviceScanner.convert("h", "a", "Bluetooth", null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("unknown connection type: Bluetooth");
    }
  }
}
