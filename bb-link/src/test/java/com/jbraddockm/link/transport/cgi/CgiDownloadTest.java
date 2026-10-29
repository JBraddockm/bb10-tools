package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.ScriptedServer;
import com.jbraddockm.link.testing.TestPhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("CgiTransport: streaming a data answer to a file")
class CgiDownloadTest {

  @TempDir private Path folder;
  private ScriptedServer server;
  private CgiTransport transport;

  @BeforeEach
  void open() throws IOException {
    server = new ScriptedServer();
    transport =
        new CgiTransport(
            new DeviceTarget(
                "127.0.0.1", server.port(), TransportScheme.HTTP, new Authentication.NoPassword()),
            ExchangeObserver.NONE);
  }

  @AfterEach
  void close() {
    transport.close();
    server.close();
  }

  private CgiRequest.Download download(Path destination, long expected) {
    return CgiRequest.Download.to(Endpoint.BACKUP, destination, expected, TestPhase.UPLOADING, "settings")
        .withText(FormField.TYPE, "settings");
  }

  @Test
  @DisplayName("writes an octet-stream answer to the file, byte for byte, and reports progress")
  void streamsToFile() throws Exception {
    var data = new byte[200 * 1024];
    for (var index = 0; index < data.length; index++) {
      data[index] = (byte) (index * 31);
    }
    server.answerBytes(200, data, "Content-Type", "application/octet-stream");
    var destination = folder.resolve("settings.tar");
    var counted = new ArrayList<Long>();

    var response = transport.send(download(destination, data.length), counted::add);

    assertThat(response.downloaded()).isNotNull();
    assertThat(response.downloaded().file()).isEqualTo(destination);
    assertThat(response.downloaded().bytes()).isEqualTo(data.length);
    assertThat(response.body()).isEmpty();
    assertThat(Files.readAllBytes(destination)).isEqualTo(data);
    assertThat(counted).isNotEmpty().isSorted();

    var request = server.requests().getFirst();
    assertThat(request.method()).isEqualTo("POST");
    assertThat(request.header("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(request.bodyText()).isEqualTo("type=settings");
  }

  @Test
  @DisplayName("an octet-stream answer of zero bytes gives an empty file and a zero count")
  void zeroBytes() throws Exception {
    server.answerBytes(200, new byte[0], "Content-Type", "application/octet-stream");
    var destination = folder.resolve("empty.tar");

    var response = transport.send(download(destination, 0), bytes -> {});

    assertThat(response.downloaded()).isNotNull();
    assertThat(response.downloaded().bytes()).isZero();
    assertThat(Files.exists(destination)).isTrue();
    assertThat(Files.size(destination)).isZero();
  }

  @Test
  @DisplayName("an XML error answer comes back as a normal body, with no download")
  void xmlErrorIsNotADownload() throws Exception {
    server.answer(
        200,
        "<RimTabletResponse><BackupGet><Status>Error</Status></BackupGet></RimTabletResponse>",
        "Content-Type",
        "text/xml");
    var destination = folder.resolve("unused.tar");

    var response = transport.send(download(destination, 0), bytes -> {});

    assertThat(response.downloaded()).isNull();
    assertThat(response.body()).contains("BackupGet");
    assertThat(Files.exists(destination)).isFalse();
  }
}
