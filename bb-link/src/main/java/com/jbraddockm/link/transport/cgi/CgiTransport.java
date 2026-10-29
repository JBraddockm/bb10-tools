package com.jbraddockm.link.transport.cgi;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import com.jbraddockm.link.transport.tls.FixedClientKeyManager;
import com.jbraddockm.link.transport.tls.PemIdentity;
import com.jbraddockm.link.transport.tls.TrustAllTls;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import javax.net.ssl.KeyManager;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;

/**
 * One HTTP connection to one device with its own cookie jar. It is configured like the original
 * tool's client: HTTP/1.1, the same user agent, a ten second connect timeout and TLS that accepts
 * the device's certificate.
 */
@SuppressWarnings(
    "deprecation") // deprecated only in newer HttpClient versions; the build pins 4.0.3
public final class CgiTransport implements AutoCloseable {

  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
  private static final String USER_AGENT = "QNXWebClient/1.0";

  private final DeviceTarget target;
  private final ExchangeObserver observer;
  private final DefaultHttpClient client = new DefaultHttpClient();
  private final BasicHttpContext context = new BasicHttpContext();
  private final BasicCookieStore cookies = new BasicCookieStore();

  public CgiTransport(DeviceTarget target, ExchangeObserver observer) throws IOException {
    this.target = target;
    this.observer = observer;
    client.getParams().setParameter("http.protocol.version", HttpVersion.HTTP_1_1);
    HttpConnectionParams.setConnectionTimeout(client.getParams(), CONNECT_TIMEOUT_MILLIS);
    HttpProtocolParams.setUserAgent(client.getParams(), USER_AGENT);
    // Older HttpClient versions, such as the pinned 4.0.3, send a request again by themselves after
    // a dropped connection, even a POST. Only the session may decide that, because repeating a
    // command can change the device twice.
    client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
    var keyManagers =
        target.authentication().certificateToOffer().isPresent()
            ? clientKeyManagers(target.authentication().certificateToOffer().get())
            : null;
    client.getConnectionManager().getSchemeRegistry().register(TrustAllTls.scheme(keyManagers));
    context.setAttribute("http.cookie-store", cookies);
  }

  private static KeyManager[] clientKeyManagers(ClientCertificate source) throws IOException {
    return new KeyManager[] {new FixedClientKeyManager(PemIdentity.load(source))};
  }

  private static String valueOf(Header header) {
    return header == null ? null : header.getValue();
  }

  public DeviceTarget target() {
    return target;
  }

  public BasicCookieStore cookies() {
    return cookies;
  }

  public CgiResponse get(String pathAndQuery) throws IOException {
    return execute(new HttpGet(checkedUrl(pathAndQuery)), "GET " + pathAndQuery, "");
  }

  CgiResponse send(CgiRequest request) throws IOException {
    return send(request, UploadObserver.NONE);
  }

  public CgiResponse send(CgiRequest request, UploadObserver observer) throws IOException {
    return switch (request) {
      case CgiRequest.Get get -> get(get.pathAndQuery());
      case CgiRequest.Form form -> {
        var body = form.encodedBody();
        var post = new HttpPost(checkedUrl(form.endpoint().path()));
        post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
        var entity = new ByteArrayEntity(body.getBytes(StandardCharsets.US_ASCII));
        entity.setContentType("application/x-www-form-urlencoded");
        post.setEntity(entity);
        yield execute(post, "POST " + form.endpoint().path(), body, form.readTimeout());
      }
      case CgiRequest.Body body -> {
        var post = new HttpPost(checkedUrl(body.pathAndQuery()));
        post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
        var entity = new ByteArrayEntity(body.text().getBytes(StandardCharsets.UTF_8));
        entity.setContentType(body.contentType());
        post.setEntity(entity);
        yield execute(post, "POST " + body.pathAndQuery(), body.text(), body.readTimeout());
      }
      case CgiRequest.Upload upload -> upload(upload, observer);
      case CgiRequest.Download download -> download(download, observer);
      case CgiRequest.Multipart multipart -> multipart(multipart, observer);
    };
  }

  /**
   * Sends one file as the single part of a {@code multipart/form-data} POST, with the rest of the
   * request in the query string: a contact photo put. The file is read into memory (photos are
   * small) and the trace gets the name and size of the file, never its content.
   */
  private CgiResponse multipart(CgiRequest.Multipart request, UploadObserver observer)
      throws IOException {
    var fileBytes = Files.readAllBytes(request.file());
    var fileName = request.file().getFileName().toString();
    var boundary = "----bblinkPhotoBoundary";
    var preamble =
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\""
                + request.fileField()
                + "\"; filename=\""
                + fileName
                + "\"\r\nContent-Type: "
                + request.fileContentType()
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);
    var epilogue = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    var body = new byte[preamble.length + fileBytes.length + epilogue.length];
    System.arraycopy(preamble, 0, body, 0, preamble.length);
    System.arraycopy(fileBytes, 0, body, preamble.length, fileBytes.length);
    System.arraycopy(epilogue, 0, body, preamble.length + fileBytes.length, epilogue.length);

    var post = new HttpPost(checkedUrl(request.pathAndQuery()));
    post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
    var entity = new ByteArrayEntity(body);
    entity.setContentType("multipart/form-data; boundary=" + boundary);
    post.setEntity(entity);
    observer.sent(fileBytes.length);
    return execute(
        post,
        "POST " + request.pathAndQuery(),
        "(multipart/form-data, "
            + fileBytes.length
            + " bytes: "
            + fileName
            + " as "
            + request.fileField()
            + ")",
        request.readTimeout());
  }

  /**
   * Sends a file as the raw body of a POST, streamed from disk with its length known. The trace
   * gets the name and the size of the file and never its content.
   */
  private CgiResponse upload(CgiRequest.Upload upload, UploadObserver observer) throws IOException {
    try (var stream =
        new CountingInputStream(Files.newInputStream(upload.file()), upload.length(), observer)) {
      var post = new HttpPost(checkedUrl(upload.pathAndQuery()));
      post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
      var entity = new InputStreamEntity(stream, upload.length());
      entity.setContentType("application/octet-stream");
      post.setEntity(entity);
      return execute(
          post,
          "POST " + upload.pathAndQuery(),
          "(application/octet-stream, "
              + upload.length()
              + " bytes: "
              + upload.file().getFileName()
              + ")",
          upload.readTimeout());
    }
  }

  /**
   * Sends a form post whose answer is streamed to a file. When the device answers with octet-stream
   * — the archive — it is written to the request's destination and the response body is left empty;
   * when it answers with anything else — an XML error envelope — that is kept in the body as usual,
   * so the conversation can read the error.
   */
  private CgiResponse download(CgiRequest.Download download, UploadObserver observer)
      throws IOException {
    var body = download.encodedBody();
    var post = new HttpPost(checkedUrl(download.endpoint().path()));
    post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
    var entity = new ByteArrayEntity(body.getBytes(StandardCharsets.US_ASCII));
    entity.setContentType("application/x-www-form-urlencoded");
    post.setEntity(entity);
    HttpConnectionParams.setSoTimeout(post.getParams(), (int) download.readTimeout().toMillis());
    var requestLine = "POST " + download.endpoint().path();
    var cookieNames =
        cookies.getCookies().stream().map(cookie -> cookie.getName()).sorted().toList();
    try {
      var response = client.execute(post, context);
      var contentType = valueOf(response.getFirstHeader("Content-Type"));
      var setCookie = valueOf(response.getFirstHeader("Set-Cookie"));
      var status = response.getStatusLine().getStatusCode();
      HttpEntity httpEntity = response.getEntity();
      CgiResponse result;
      if (isStreamedAnswer(contentType)) {
        var written = streamToFile(httpEntity, download, observer);
        result =
            new CgiResponse(
                status,
                contentType,
                "",
                setCookie,
                new CgiResponse.Downloaded(download.destination(), written));
      } else {
        // Not the data stream: an error envelope the device sent as XML. Keep it so the
        // conversation can read the error, exactly as an ordinary answer would be kept.
        var bytes = httpEntity == null ? new byte[0] : EntityUtils.toByteArray(httpEntity);
        result =
            new CgiResponse(
                status, contentType, new String(bytes, StandardCharsets.UTF_8), setCookie);
      }
      this.observer.onExchange(requestLine, body, cookieNames, result);
      return result;
    } catch (IOException failure) {
      this.observer.onFailedExchange(requestLine, body, cookieNames, failure);
      throw failure;
    }
  }

  /**
   * Whether a download's answer is the data stream (written to the file) rather than an error
   * envelope (kept in the body). The backup archive comes as {@code octet-stream}; a contact photo
   * comes as an {@code image/…} type, while an error — "not modified", "unauthorised" — comes as
   * JSON or XML and must stay in the body so the conversation can read it.
   */
  private static boolean isStreamedAnswer(String contentType) {
    if (contentType == null) {
      return false;
    }
    var lower = contentType.toLowerCase(java.util.Locale.ROOT);
    return lower.contains("octet-stream") || lower.contains("image/");
  }

  /** Copies the answer to the download's file, reporting bytes the way an upload reports them. */
  private static long streamToFile(
      HttpEntity entity, CgiRequest.Download download, UploadObserver observer) throws IOException {
    var step = Math.max(64 * 1024L, download.expectedLength() / 100);
    long total = 0;
    long lastReported = 0;
    var buffer = new byte[64 * 1024];
    try (var in = entity == null ? InputStream.nullInputStream() : entity.getContent();
        var out = Files.newOutputStream(download.destination())) {
      int read;
      while ((read = in.read(buffer)) >= 0) {
        out.write(buffer, 0, read);
        total += read;
        if (total - lastReported >= step) {
          lastReported = total;
          observer.sent(total);
        }
      }
    }
    return total;
  }

  private String checkedUrl(String pathAndQuery) {
    var url = target.url(pathAndQuery);
    if (URI.create(url).getHost() == null) {
      throw new InvalidArgumentException("host", "is not a usable host name or address");
    }
    return url;
  }

  private CgiResponse execute(HttpUriRequest request, String requestLine, String requestBody)
      throws IOException {
    return execute(request, requestLine, requestBody, CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  private CgiResponse execute(
      HttpUriRequest request, String requestLine, String requestBody, Duration readTimeout)
      throws IOException {
    HttpConnectionParams.setSoTimeout(request.getParams(), (int) readTimeout.toMillis());
    var cookieNames =
        cookies.getCookies().stream().map(cookie -> cookie.getName()).sorted().toList();
    try {
      var response = client.execute(request, context);
      HttpEntity entity = response.getEntity();
      var bytes = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
      var contentType = response.getFirstHeader("Content-Type");
      var setCookie = response.getFirstHeader("Set-Cookie");
      var result =
          new CgiResponse(
              response.getStatusLine().getStatusCode(),
              valueOf(contentType),
              new String(bytes, StandardCharsets.UTF_8),
              valueOf(setCookie));
      observer.onExchange(requestLine, requestBody, cookieNames, result);
      return result;
    } catch (IOException failure) {
      observer.onFailedExchange(requestLine, requestBody, cookieNames, failure);
      throw failure;
    }
  }

  @Override
  public void close() {
    client.getConnectionManager().shutdown();
  }
}
