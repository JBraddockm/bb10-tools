package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link FormField}. */
@DisplayName("FormField: the fields of a form")
class FormFieldTest {

  @ParameterizedTest(name = "{0} is \"{1}\"")
  @CsvSource({
    "GET_DYNAMIC_PROPERTIES, Get Dynamic Properties",
    "ACTION, action",
    "NAME, name",
    "VALUE, value",
    "WIPE, wipe",
    "MODE, mode",
    "SIZE, size",
    "TYPE, type",
    "DEVICE_ID, deviceid",
    "ECOID, ecoid",
    "CERT_DATA, certdata",
    "PACKAGE_ID, packageid",
    "STATUS, status",
    "QUERY, query",
    "OPT, opt",
    "TOTAL_SIZE, totalsize",
    "PKGTYPE, pkgtype",
    "COMMAND, command",
    "SERVICE, service",
    "WORKSTATION_ID, workstation_id",
    "DATA, data",
    "PATH, path",
    "UPDATED_TIME, updated_time"
  })
  @DisplayName("each field is written with the name that the device reads")
  void eachFieldHasItsWireName(FormField field, String wireName) {
    assertThat(field.wireName()).isEqualTo(wireName);
  }

  @Test
  @DisplayName("no two fields have the same name, and there are no others than these")
  void thereAreTwentyThreeDistinctFields() {
    assertThat(Arrays.stream(FormField.values()).map(FormField::wireName))
        .doesNotHaveDuplicates()
        .hasSize(23);
  }
}
