package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link Endpoint}: the programs of the device and where each is served. */
@DisplayName("Endpoint: the CGI programs of the device")
class EndpointTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "DYNAMIC_PROPERTIES, dynamicProperties.cgi",
    "DISCOVERY, discovery.cgi",
    "UPDATE, update.cgi",
    "BACKUP, backup.cgi",
    "WIPE, wipe.cgi",
    "RESET, reset.cgi",
    "SUPPORT, support.cgi",
    "LOGIN, login.cgi",
    "PAIRING, pairing.cgi",
    "PIM_SYNC, pimsync.cgi"
  })
  @DisplayName("each endpoint is the file of its program, and its path is under /cgi-bin/")
  void eachEndpointIsAProgramUnderCgiBin(Endpoint endpoint, String fileName) {
    assertThat(endpoint.fileName()).isEqualTo(fileName);
    assertThat(endpoint.path()).isEqualTo("/cgi-bin/" + fileName);
  }

  @Test
  @DisplayName("there is no endpoint that this table does not know")
  void thereAreTenEndpoints() {
    assertThat(Arrays.stream(Endpoint.values()).map(Endpoint::fileName))
        .doesNotHaveDuplicates()
        .hasSize(10);
  }
}
