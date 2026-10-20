package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ResponseElement}. */
@DisplayName("ResponseElement: the elements of an answer that are known by name")
class ResponseElementTest {

  @ParameterizedTest(name = "{0} is <{1}>")
  @CsvSource({
    "DYNAMIC_PROPERTIES, DynamicProperties",
    "DEVICE_PIN, DevicePIN",
    "AUTH_CHALLENGE, AuthChallenge",
    "AUTH, Auth",
    "UPDATE_START, UpdateStart",
    "UPDATE_SEND, UpdateSend",
    "UPDATE_PROGRESS, UpdateProgress",
    "UPDATE_END, UpdateEnd",
    "DELETE_REQUEST, DeleteRequest",
    "DELETE_PROGRESS, DeleteProgress"
  })
  @DisplayName("each element is written with the name that the device uses")
  void eachElementHasItsName(ResponseElement element, String name) {
    assertThat(element.elementName()).isEqualTo(name);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(ResponseElement.class)
  @DisplayName("of finds each element by its name")
  void ofFindsEachElementByName(ResponseElement element) {
    assertThat(ResponseElement.of(element.elementName())).contains(element);
  }

  @ParameterizedTest(name = "\"{0}\" is not known")
  @ValueSource(strings = {"Strange", "devicepin", "DEVICEPIN", "DevicePIN ", ""})
  @DisplayName("of gives nothing for a name that is not known, and takes no other case or blanks")
  void ofGivesNothingForUnknownNames(String name) {
    assertThat(ResponseElement.of(name)).isEmpty();
  }

  @Test
  @DisplayName("no two elements have the same name")
  void theNamesAreDistinct() {
    assertThat(Arrays.stream(ResponseElement.values()).map(ResponseElement::elementName))
        .doesNotHaveDuplicates();
  }
}
