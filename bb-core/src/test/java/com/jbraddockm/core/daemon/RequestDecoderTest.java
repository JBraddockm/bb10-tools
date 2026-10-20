package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.daemon.RequestDecoder.Decoding;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Tests for {@link RequestDecoder}: valid requests are accepted; malformed ones are rejected with
 * {@link CoreErrorCode#INVALID_REQUEST}, carrying the request id when it can be read.
 */
@DisplayName("RequestDecoder: a line of the protocol as a request")
class RequestDecoderTest {

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(
      resources = "/com/jbraddockm/core/daemon/decoder--lines.csv",
      delimiter = '|',
      quoteCharacter = '`')
  @DisplayName(
      "a line that is not a request is rejected as INVALID_REQUEST, with the identifier if there is"
          + " one, and the words that say why")
  void aLineThatIsNotARequest(String line, String id, String message) {
    var decoding = RequestDecoder.decode(line);

    assertThat(decoding)
        .isInstanceOfSatisfying(
            Decoding.Rejected.class,
            rejected -> {
              assertThat(rejected.error().code()).isEqualTo(CoreErrorCode.INVALID_REQUEST);
              assertThat(rejected.error().message()).isEqualTo(message);
              assertThat(rejected.id() == null ? "[none]" : Json.write(rejected.id().toJson()))
                  .isEqualTo(id);
            });
  }

  @ParameterizedTest(name = "{0}")
  @CsvFileSource(
      resources = "/com/jbraddockm/core/daemon/decoder--accepted.csv",
      delimiter = '|',
      quoteCharacter = '`')
  @DisplayName(
      "a line that is a request is accepted, with its identifier, its method and its parameters,"
          + " which are none when there are none")
  void aLineThatIsARequest(String line, String id, String method, String names) {
    var decoding = RequestDecoder.decode(line);

    assertThat(decoding)
        .isInstanceOfSatisfying(
            Decoding.Accepted.class,
            accepted -> {
              assertThat(Json.write(accepted.request().id().toJson())).isEqualTo(id);
              assertThat(accepted.request().method()).isEqualTo(method == null ? "" : method);
              assertThat(List.copyOf(accepted.request().params().names()).toString())
                  .isEqualTo(names);
            });
  }

  @Test
  @DisplayName(
      "a request whose method is not one that the service has is still a request: it is the server"
          + " that rejects the method")
  void anyTextIsAMethod() {
    var line = Resources.text(RequestDecoderTest.class, "decoder--unknown-method.json");

    assertThat(RequestDecoder.decode(line)).isInstanceOf(Decoding.Accepted.class);
  }
}
