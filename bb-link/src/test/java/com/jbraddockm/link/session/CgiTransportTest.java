package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.QueryParameter;
import com.jbraddockm.link.cgi.UploadObserver;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.ScriptedServer;
import com.jbraddockm.link.testing.TestPhase;
import com.jbraddockm.link.testing.TlsTestDevice;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.NoHttpResponseException;
import org.apache.http.cookie.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CgiTransport} against a {@link ScriptedServer}: requests, cookies, uploads and
 * failures.
 */
@SuppressWarnings("deprecation")
@DisplayName("CgiTransport: what goes over the wire to a device")
class CgiTransportTest {

  private record Observed(
      String requestLine, String requestBody, List<String> cookieNames, CgiResponse response) {}

  @TempDir private Path folder;
  private ScriptedServer server;
  private CgiTransport transport;
  private final List<Observed> observed = new ArrayList<>();

  private DeviceTarget plain(int port) {
    return new DeviceTarget(
        "127.0.0.1", port, TransportScheme.HTTP, new Authentication.NoPassword());
  }

  @BeforeEach
  void openTheTransport() throws IOException {
    server = new ScriptedServer();
    transport =
        new CgiTransport(
            plain(server.port()),
            (line, body, cookies, response) ->
                observed.add(new Observed(line, body, cookies, response)));
  }

  @AfterEach
  void closeEverything() {
    transport.close();
    server.close();
  }

  private static String answerXml() {
    return Resources.text(CgiTransportTest.class, "transport--answer.xml");
  }

  @Nested
  @DisplayName("a GET")
  class Getting {

    @Test
    @DisplayName(
        "goes to the path and the query that it was given, with the agent of the original tool")
    void goesToThePathAndQuery() throws Exception {
      server.answer(200, answerXml());

      transport.get("/cgi-bin/login.cgi?request_version=1");

      var request = server.requests().getFirst();
      assertThat(request.method()).isEqualTo("GET");
      assertThat(request.target()).isEqualTo("/cgi-bin/login.cgi?request_version=1");
      assertThat(request.header("User-Agent")).isEqualTo("QNXWebClient/1.0");
    }

    @Test
    @DisplayName("gives the status, the content type, the body and the cookie that the device sent")
    void givesWhatTheDeviceSent() throws Exception {
      server.answer(
          200, answerXml(), "Content-Type", "text/xml", "Set-Cookie", "dtmauth=TOKEN; path=/");

      var response = transport.get("/cgi-bin/x");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.contentType()).isEqualTo("text/xml");
      assertThat(response.body()).isEqualTo(answerXml());
      assertThat(response.setCookie()).isEqualTo("dtmauth=TOKEN; path=/");
    }

    @Test
    @DisplayName(
        "gives an empty body for an answer with none, and no content type or cookie that was not"
            + " sent")
    void givesAnEmptyBody() throws Exception {
      server.answer(200, "");

      var response = transport.get("/cgi-bin/x");

      assertThat(response.body()).isEmpty();
      assertThat(response.setCookie()).isNull();
    }

    @Test
    @DisplayName(
        "gives the status of an answer that is not a success, and its body: it is for the session"
            + " to judge")
    void givesAStatusThatIsNotASuccess() throws Exception {
      server.answer(401, answerXml());

      var response = transport.get("/cgi-bin/x");

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.body()).isEqualTo(answerXml());
    }

    @Test
    @DisplayName("reads a body as UTF-8, and writes what is not valid as the replacement character")
    void readsUtf8() throws Exception {
      server.answerBytes(200, new byte[] {'a', (byte) 0xFF, 'b'});

      assertThat(transport.get("/cgi-bin/x").body()).isEqualTo("a\uFFFDb");
    }
  }

  @Nested
  @DisplayName("a request of each kind")
  class Sending {

    @Test
    @DisplayName(
        "a form is a POST of its fields, as ASCII, in application/x-www-form-urlencoded, and does"
            + " not wait for 100-continue")
    void aForm() throws Exception {
      server.answer(200, answerXml());
      var form =
          CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES).echo(FormField.GET_DYNAMIC_PROPERTIES);

      transport.send(form);

      var request = server.requests().getFirst();
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.target()).isEqualTo("/cgi-bin/dynamicProperties.cgi");
      assertThat(request.header("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
      assertThat(request.bodyText()).isEqualTo(form.encodedBody());
      assertThat(request.header("Content-Length"))
          .isEqualTo(String.valueOf(form.encodedBody().length()));
      assertThat(request.header("Expect")).isNull();
    }

    @Test
    @DisplayName("a get request is the path and the query that it says")
    void aGetRequest() throws Exception {
      server.answer(200, answerXml());

      transport.send(CgiRequest.Get.to(Endpoint.PIM_SYNC).withText(QueryParameter.COMMAND, "list"));

      assertThat(server.requests().getFirst().target())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=list");
    }

    @Test
    @DisplayName(
        "a text with a content type is a POST of the text as UTF-8, to the path and the query, with"
            + " that content type")
    void aBody() throws Exception {
      server.answer(200, answerXml());
      var text = Resources.text(CgiTransportTest.class, "transport--utf8-body.txt");
      var body =
          CgiRequest.Body.of(Endpoint.LOGIN, text, "application/octet-stream")
              .with(QueryParameter.REQUEST_VERSION, "3")
              .with(QueryParameter.CGI_AUTH, "1");

      transport.send(body);

      var request = server.requests().getFirst();
      assertThat(request.method()).isEqualTo("POST");
      assertThat(request.target()).isEqualTo("/cgi-bin/login.cgi?request_version=3&cgiauth=1");
      assertThat(request.header("Content-Type")).isEqualTo("application/octet-stream");
      assertThat(request.body()).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
      assertThat(request.header("Expect")).isNull();
    }

    @Test
    @DisplayName(
        "a file is a POST of its bytes, unchanged, in application/octet-stream, and the observer is"
            + " told how many were read")
    void anUpload() throws Exception {
      var data = new byte[200 * 1024];
      for (var index = 0; index < data.length; index++) {
        data[index] = (byte) (index * 31);
      }
      var file = Files.write(folder.resolve("package.bar"), data);
      var counted = new ArrayList<Long>();
      UploadObserver watcher = counted::add;
      server.answer(200, answerXml());

      transport.send(
          CgiRequest.Upload.of(
              Endpoint.UPDATE, file, data.length, TestPhase.UPLOADING, "package.bar"),
          watcher);

      var request = server.requests().getFirst();
      assertThat(request.body()).isEqualTo(data);
      assertThat(request.header("Content-Type")).isEqualTo("application/octet-stream");
      assertThat(request.header("Content-Length")).isEqualTo(String.valueOf(data.length));
      assertThat(counted).isNotEmpty().isSorted();
      assertThat(counted.getLast()).isLessThanOrEqualTo((long) data.length);
    }
  }

  @Nested
  @DisplayName("the cookies")
  class Cookies {

    @Test
    @DisplayName("a cookie that the device sets is sent back with the next request")
    void aCookieIsSentBack() throws Exception {
      server.answer(200, answerXml(), "Set-Cookie", "dtmauth=TOKEN; path=/");
      server.answer(200, answerXml());

      transport.get("/cgi-bin/one");
      transport.get("/cgi-bin/two");

      assertThat(server.requests().get(0).header("Cookie")).isNull();
      assertThat(server.requests().get(1).header("Cookie")).contains("dtmauth=TOKEN");
    }

    @Test
    @DisplayName("keeps the cookie in a store that the session can read")
    void theStoreHoldsTheCookie() throws Exception {
      server.answer(200, answerXml(), "Set-Cookie", "dtmauth=TOKEN; path=/");

      transport.get("/cgi-bin/one");

      assertThat(transport.cookies().getCookies())
          .extracting(Cookie::getName)
          .containsExactly("dtmauth");
    }
  }

  @Nested
  @DisplayName("what the observer is told")
  class Observing {

    @Test
    @DisplayName(
        "each exchange: the request line, its body, the names of the cookies sent, and the answer")
    void eachExchange() throws Exception {
      server.answer(200, answerXml(), "Set-Cookie", "dtmauth=TOKEN; path=/");
      server.answer(200, answerXml());
      var form =
          CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES).echo(FormField.GET_DYNAMIC_PROPERTIES);

      transport.get("/cgi-bin/login.cgi?request_version=1");
      transport.send(form);

      assertThat(observed).hasSize(2);
      assertThat(observed.get(0).requestLine())
          .isEqualTo("GET /cgi-bin/login.cgi?request_version=1");
      assertThat(observed.get(0).requestBody()).isEmpty();
      assertThat(observed.get(0).cookieNames()).isEmpty();
      assertThat(observed.get(1).requestLine()).isEqualTo("POST /cgi-bin/dynamicProperties.cgi");
      assertThat(observed.get(1).requestBody()).isEqualTo(form.encodedBody());
      assertThat(observed.get(1).cookieNames()).containsExactly("dtmauth");
      assertThat(observed.get(1).response().statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("a file is shown by its kind, its size and its name, and never by its content")
    void anUploadIsDescribed() throws Exception {
      var file = Files.write(folder.resolve("package.bar"), new byte[10]);
      server.answer(200, answerXml());

      transport.send(
          CgiRequest.Upload.of(Endpoint.UPDATE, file, 10, TestPhase.UPLOADING, "package.bar"));

      assertThat(observed.getFirst().requestLine()).isEqualTo("POST /cgi-bin/update.cgi");
      assertThat(observed.getFirst().requestBody())
          .isEqualTo("(application/octet-stream, 10 bytes: package.bar)");
    }
  }

  @Nested
  @DisplayName("what goes wrong")
  class GoingWrong {

    @Test
    @DisplayName("a connection that is refused is a ConnectException")
    void aRefusedConnection() throws Exception {
      var closed = new ScriptedServer();
      var port = closed.port();
      closed.close();
      try (var refused = new CgiTransport(plain(port), ExchangeObserver.NONE)) {
        assertThatThrownBy(() -> refused.get("/cgi-bin/x")).isInstanceOf(ConnectException.class);
      }
    }

    @Test
    @DisplayName(
        "an answer that takes longer than the request is willing to wait is a"
            + " SocketTimeoutException")
    void aTimeout() {
      server.answerAfter(Duration.ofSeconds(3), answerXml());
      var impatient = CgiRequest.Form.to(Endpoint.SUPPORT).waitingUpTo(Duration.ofMillis(200));

      assertThatThrownBy(() -> transport.send(impatient))
          .isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    @DisplayName(
        "a connection that is dropped is not tried again by the transport: one request arrives, and"
            + " the failure is told")
    void aDroppedConnectionIsNotRepeated() {
      server.dropConnection();
      server.answer(200, answerXml());
      var form = CgiRequest.Form.to(Endpoint.WIPE).echo(FormField.WIPE);

      assertThatThrownBy(() -> transport.send(form)).isInstanceOf(NoHttpResponseException.class);

      assertThat(server.requests()).hasSize(1);
    }

    @Test
    @DisplayName(
        "a host that is not a usable name is refused as an argument, before anything is sent")
    void aHostThatIsNotUsable() throws Exception {
      var odd =
          new DeviceTarget(
              "device_name", 443, TransportScheme.HTTP, new Authentication.NoPassword());
      try (var badTransport = new CgiTransport(odd, ExchangeObserver.NONE)) {
        assertThatThrownBy(() -> badTransport.get("/cgi-bin/x"))
            .isInstanceOfSatisfying(
                InvalidArgumentException.class,
                invalid -> assertThat(invalid.parameter()).isEqualTo("host"));
      }
    }

    @Test
    @DisplayName("a transport that was closed cannot be used")
    void aClosedTransport() throws Exception {
      var closing = new CgiTransport(plain(server.port()), ExchangeObserver.NONE);
      closing.close();

      assertThatThrownBy(() -> closing.get("/cgi-bin/x")).isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  @DisplayName("says which target it is for")
  void saysItsTarget() {
    assertThat(transport.target()).isEqualTo(plain(server.port()));
  }

  @Test
  @DisplayName(
      "takes the certificate of any device over HTTPS, though no authority lists it and the address"
          + " is not in it")
  void acceptsAnyDeviceCertificate() throws Exception {
    try (var device = new TlsTestDevice();
        var secure =
            new CgiTransport(
                new DeviceTarget(
                    "127.0.0.1",
                    device.port(),
                    TransportScheme.HTTPS,
                    new Authentication.NoPassword()),
                ExchangeObserver.NONE)) {
      var response = secure.get("/cgi-bin/login.cgi?request_version=1");

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("Success");
    }
  }
}
