package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** Tests for {@link ConnectionPath}: classifying a host by its address. */
@DisplayName("ConnectionPath: which network path an address is reached over")
class ConnectionPathTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvFileSource(
      resources = "/com/jbraddockm/link/transport/session/connection-path--addresses.csv",
      delimiter = '|',
      useHeadersInDisplayName = true)
  @DisplayName("classifies USB link-local, tunnel ULAs, private LAN ranges and everything else")
  void classifiesByAddress(String address, ConnectionPath expected) {
    assertThat(ConnectionPath.of(address)).isEqualTo(expected);
  }
}
