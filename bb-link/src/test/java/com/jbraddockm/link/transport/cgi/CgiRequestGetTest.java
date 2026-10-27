package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CgiRequest.Get}, including the immutability of its {@code with...} methods. */
@DisplayName("CgiRequest.Get: a GET with a query")
class CgiRequestGetTest {

  private static final CgiRequest.Get BASE = CgiRequest.Get.to(Endpoint.PIM_SYNC);

  @Test
  @DisplayName(
      "to makes a request with no query, no purpose, and an empty answer that is not normal")
  void toMakesAnEmptyRequest() {
    assertThat(BASE.endpoint()).isEqualTo(Endpoint.PIM_SYNC);
    assertThat(BASE.query()).isEmpty();
    assertThat(BASE.purpose()).isEmpty();
    assertThat(BASE.emptyAnswerIsNormal()).isFalse();
    assertThat(BASE.readTimeout()).isEqualTo(CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName("pathAndQuery is the path alone when there is no query")
  void pathAndQueryWithoutAQuery() {
    assertThat(BASE.pathAndQuery()).isEqualTo("/cgi-bin/pimsync.cgi");
  }

  @Test
  @DisplayName("pathAndQuery writes the parameters in the order they were added, encoded")
  void pathAndQueryEncodesAndKeepsTheOrder() {
    var request =
        BASE.withText(QueryParameter.COMMAND, "list_accounts")
            .withText(QueryParameter.WORKSTATION_ID, "a b&c=d/é");

    assertThat(request.pathAndQuery())
        .isEqualTo(
            "/cgi-bin/pimsync.cgi?command=list_accounts&workstation_id=a%20b%26c%3Dd%2F%C3%A9");
  }

  @Test
  @DisplayName("withText sets a parameter again in the place it had, with the new value")
  void withTextReplacesInPlace() {
    var request =
        BASE.withText(QueryParameter.COMMAND, "first")
            .withText(QueryParameter.LIMIT, "5")
            .withText(QueryParameter.COMMAND, "second");

    assertThat(request.query())
        .containsExactly(
            Map.entry(QueryParameter.COMMAND, "second"), Map.entry(QueryParameter.LIMIT, "5"));
  }

  @Test
  @DisplayName("with writes the wire name of a value that has one")
  void withWritesTheWireName() {
    assertThat(BASE.with(QueryParameter.COMMAND, FormField.ACTION).query())
        .containsExactly(Map.entry(QueryParameter.COMMAND, "action"));
  }

  @Test
  @DisplayName("a request is not changed by adding a parameter to it")
  void aRequestIsImmutable() {
    BASE.withText(QueryParameter.LIMIT, "5");

    assertThat(BASE.query()).isEmpty();
    assertThat(BASE.pathAndQuery()).isEqualTo("/cgi-bin/pimsync.cgi");
  }

  @Test
  @DisplayName(
      "the query cannot be changed through the request, nor through the map it was made from")
  void theQueryIsACopyThatCannotBeChanged() {
    var made = new LinkedHashMap<QueryParameter, String>();
    made.put(QueryParameter.LIMIT, "5");
    var request = new CgiRequest.Get(Endpoint.PIM_SYNC, made, "", false);

    made.put(QueryParameter.OFFSET, "1");

    assertThat(request.query()).containsOnlyKeys(QueryParameter.LIMIT);
    assertThatThrownBy(() -> request.query().put(QueryParameter.OFFSET, "1"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName(
      "describe says GET and the program when there is no purpose, and the purpose when there is")
  void describeSaysWhatTheRequestDoes() {
    assertThat(BASE.describe()).isEqualTo("GET pimsync.cgi");
    assertThat(BASE.describedAs("Reading the contacts").describe())
        .isEqualTo("Reading the contacts");
  }

  @Test
  @DisplayName(
      "expectingNoBody says that an empty answer is normal, and keeps saying it after more"
          + " parameters")
  void expectingNoBodySurvivesMoreParameters() {
    var request = BASE.expectingNoBody();

    assertThat(request.emptyAnswerIsNormal()).isTrue();
    assertThat(request.withText(QueryParameter.LIMIT, "5").emptyAnswerIsNormal()).isTrue();
    assertThat(request.describedAs("x").emptyAnswerIsNormal()).isTrue();
  }

  @Test
  @DisplayName("an endpoint and a purpose are required")
  void anEndpointAndAPurposeAreRequired() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CgiRequest.Get(null, new LinkedHashMap<>(), "", false))
        .withMessage("endpoint");
    assertThatNullPointerException()
        .isThrownBy(() -> new CgiRequest.Get(Endpoint.PIM_SYNC, new LinkedHashMap<>(), null, false))
        .withMessage("purpose");
  }
}
