package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.xml.MalformedXmlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link ResponseEnvelope}: locating the payload of an answer. */
@DisplayName("ResponseEnvelope: the payload of an answer, inside its wrapper")
class ResponseEnvelopeTest {

  private static ResponseEnvelope envelope(String resource) {
    return ResponseEnvelope.parse(Resources.text(ResponseEnvelopeTest.class, resource));
  }

  @ParameterizedTest(name = "{1}: {2}")
  @CsvSource({
    "response-envelope--plain.xml, DynamicProperties, the first child of the root",
    "response-envelope--legacy-root.xml, DynamicProperties, whatever the root is called",
    "response-envelope--revision-then-payload.xml, DevicePIN, a revision marker is skipped for what"
        + " follows it",
    "response-envelope--payload-in-revision.xml, DevicePIN, a payload inside the revision marker",
    "response-envelope--revision-then-two-payloads.xml, Auth, the first of two that follow the"
        + " marker",
    "response-envelope--two-revisions.xml, Auth, a second marker is skipped like the first",
    "response-envelope--without-content.xml, R, a root with no content is its own payload",
    "response-envelope--revision-only.xml, Rev, a marker with nothing else is all there is",
    "response-envelope--unknown-element.xml, Strange, a payload that is not known by name"
  })
  @DisplayName("parse finds the payload")
  void parseFindsThePayload(String resource, String payload, String rule) {
    assertThat(envelope(resource).name()).as(rule).isEqualTo(payload);
    assertThat(envelope(resource).payload().name()).isEqualTo(payload);
  }

  @Test
  @DisplayName("element gives the known element that the payload is")
  void elementGivesTheKnownElement() {
    var known = envelope("response-envelope--known-element.xml");

    assertThat(known.element()).contains(ResponseElement.DEVICE_PIN);
    assertThat(known.payload().text()).isEqualTo("2A3B4C5D");
  }

  @Test
  @DisplayName("element gives nothing for a payload that is not known by name")
  void elementGivesNothingForAnUnknownPayload() {
    assertThat(envelope("response-envelope--unknown-element.xml").element()).isEmpty();
  }

  @Test
  @DisplayName("parse refuses a document that is not well-formed")
  void parseRefusesMalformedXml() {
    var text = Resources.text(ResponseEnvelopeTest.class, "response-envelope--malformed.xml");

    assertThatExceptionOfType(MalformedXmlException.class)
        .isThrownBy(() -> ResponseEnvelope.parse(text));
  }
}
