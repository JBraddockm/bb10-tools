package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link QueryParameter}. */
@DisplayName("QueryParameter: the parameters of a query")
class QueryParameterTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "COMMAND, command",
    "SERVICE, service",
    "WORKSTATION_ID, workstation_id",
    "WORKSTATION_NAME, workstation_name",
    "OBJECT_ID, object_id",
    "UPDATED_TIME, updated_time",
    "LIMIT, limit",
    "DIRTY_ONLY, dirty_only",
    "LOCAL_ONLY, local_only",
    "OFFSET, offset",
    "TYPE, type",
    "REQUEST_VERSION, request_version",
    "CGI_AUTH, cgiauth",
    "ACTION, action",
    "SIZE, size"
  })
  @DisplayName("each parameter is written with the name that the device reads")
  void eachParameterHasItsWireName(QueryParameter parameter, String wireName) {
    assertThat(parameter.wireName()).isEqualTo(wireName);
  }

  @Test
  @DisplayName("no two parameters have the same name, and there are no others than these")
  void thereAreFifteenDistinctParameters() {
    assertThat(Arrays.stream(QueryParameter.values()).map(QueryParameter::wireName))
        .doesNotHaveDuplicates()
        .hasSize(15);
  }
}
