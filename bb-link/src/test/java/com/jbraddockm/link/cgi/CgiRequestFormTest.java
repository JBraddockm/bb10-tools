package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.testing.Resources;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CgiRequest.Form}. */
@DisplayName("CgiRequest.Form: a POST of the fields of a form")
class CgiRequestFormTest {

  private static final CgiRequest.Form BASE = CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES);

  @Test
  @DisplayName("to makes a form with no fields, a body that is empty, and the usual time to wait")
  void toMakesAnEmptyForm() {
    assertThat(BASE.fields()).isEmpty();
    assertThat(BASE.encodedBody()).isEmpty();
    assertThat(BASE.emptyAnswerIsNormal()).isFalse();
    assertThat(BASE.readTimeout()).isEqualTo(CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName(
      "encodedBody writes name=value for each field, encoded, in the order they were added")
  void encodedBodyKeepsTheOrder() {
    var form = BASE.echo(FormField.GET_DYNAMIC_PROPERTIES).withText(FormField.NAME, "a b");

    assertThat(form.encodedBody())
        .isEqualTo("Get%20Dynamic%20Properties=Get%20Dynamic%20Properties&name=a%20b");
  }

  @Test
  @DisplayName("encodedBody writes the lines of a value as %0A, as a certificate in PEM needs")
  void encodedBodyWritesLineBreaks() {
    var value = Resources.text(CgiRequestFormTest.class, "form-body--multiline-value.txt");
    var expected =
        Resources.text(CgiRequestFormTest.class, "form-body--multiline-value--expected.txt")
            .strip();

    assertThat(BASE.withText(FormField.CERT_DATA, value).encodedBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("with writes the wire name of a value that has one")
  void withWritesTheWireName() {
    assertThat(BASE.with(FormField.TYPE, FormField.WIPE).fields())
        .containsExactly(Map.entry(FormField.TYPE, "wipe"));
  }

  @Test
  @DisplayName("echo sends a field with its own name as its value")
  void echoSendsTheNameAsTheValue() {
    assertThat(BASE.echo(FormField.ACTION).fields())
        .containsExactly(Map.entry(FormField.ACTION, "action"));
  }

  @Test
  @DisplayName("withText sets a field again in the place it had, with the new value")
  void withTextReplacesInPlace() {
    var form =
        BASE.withText(FormField.NAME, "first")
            .withText(FormField.VALUE, "v")
            .withText(FormField.NAME, "second");

    assertThat(form.fields())
        .containsExactly(Map.entry(FormField.NAME, "second"), Map.entry(FormField.VALUE, "v"));
  }

  @Test
  @DisplayName("a form is not changed by adding a field to it, and its fields cannot be changed")
  void aFormIsImmutable() {
    var form = BASE.withText(FormField.NAME, "x");

    assertThat(BASE.fields()).isEmpty();
    assertThatThrownBy(() -> form.fields().put(FormField.VALUE, "y"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName(
      "describe says POST and the program when there is no purpose, and the purpose when there is")
  void describeSaysWhatTheFormDoes() {
    assertThat(BASE.describe()).isEqualTo("POST dynamicProperties.cgi");
    assertThat(BASE.describedAs("Reading the device PIN").describe())
        .isEqualTo("Reading the device PIN");
  }

  @Test
  @DisplayName(
      "expectingNoBody says that an empty answer is normal, and keeps saying it afterwards")
  void expectingNoBodySurvives() {
    var form = BASE.expectingNoBody();

    assertThat(form.emptyAnswerIsNormal()).isTrue();
    assertThat(form.withText(FormField.NAME, "x").emptyAnswerIsNormal()).isTrue();
    assertThat(form.waitingUpTo(Duration.ofMinutes(2)).emptyAnswerIsNormal()).isTrue();
  }

  @Test
  @DisplayName("waitingUpTo sets the time to wait for the answer, and keeps it after more fields")
  void waitingUpToSetsTheTime() {
    var form = BASE.waitingUpTo(Duration.ofMinutes(5));

    assertThat(form.readTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(form.withText(FormField.NAME, "x").readTimeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(BASE.readTimeout()).isEqualTo(CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName("an endpoint and a purpose are required")
  void anEndpointAndAPurposeAreRequired() {
    var time = CgiRequest.DEFAULT_READ_TIMEOUT;

    assertThatNullPointerException()
        .isThrownBy(() -> new CgiRequest.Form(null, new LinkedHashMap<>(), "", false, time))
        .withMessage("endpoint");
    assertThatNullPointerException()
        .isThrownBy(
            () -> new CgiRequest.Form(Endpoint.WIPE, new LinkedHashMap<>(), null, false, time))
        .withMessage("purpose");
  }
}
