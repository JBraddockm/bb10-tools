package com.jbraddockm.link.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
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
final class CgiTransport implements AutoCloseable {

  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
  private static final int READ_TIMEOUT_MILLIS = 30_000;
  private static final String USER_AGENT = "QNXWebClient/1.0";

  private final DeviceTarget target;
  private final ExchangeObserver observer;
  private final DefaultHttpClient client = new DefaultHttpClient();
  private final BasicHttpContext context = new BasicHttpContext();
  private final BasicCookieStore cookies = new BasicCookieStore();

  CgiTransport(DeviceTarget target, ExchangeObserver observer) {
    this.target = target;
    this.observer = observer;
    client.getParams().setParameter("http.protocol.version", HttpVersion.HTTP_1_1);
    HttpConnectionParams.setConnectionTimeout(client.getParams(), CONNECT_TIMEOUT_MILLIS);
    HttpProtocolParams.setUserAgent(client.getParams(), USER_AGENT);
    // Older HttpClient versions, such as the pinned 4.0.3, send a request again by themselves after
    // a dropped connection, even a POST. Only the session may decide that, because repeating a
    // command can change the device twice.
    client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(0, false));
    client.getConnectionManager().getSchemeRegistry().register(TrustAllTls.scheme());
    context.setAttribute("http.cookie-store", cookies);
  }

  DeviceTarget target() {
    return target;
  }

  BasicCookieStore cookies() {
    return cookies;
  }

  CgiResponse get(String pathAndQuery) throws IOException {
    return execute(new HttpGet(checkedUrl(pathAndQuery)), "GET " + pathAndQuery, "");
  }

  CgiResponse send(CgiRequest request) throws IOException {
    return switch (request) {
      case CgiRequest.Get get -> get(get.pathAndQuery());
      case CgiRequest.Form form -> {
        var body = form.encodedBody();
        var post = new HttpPost(checkedUrl(form.endpoint().path()));
        post.getParams().setBooleanParameter("http.protocol.expect-continue", false);
        var entity = new ByteArrayEntity(body.getBytes(StandardCharsets.US_ASCII));
        entity.setContentType("application/x-www-form-urlencoded");
        post.setEntity(entity);
        yield execute(post, "POST " + form.endpoint().path(), body);
      }
    };
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
    HttpConnectionParams.setSoTimeout(request.getParams(), READ_TIMEOUT_MILLIS);
    var cookieNames =
        cookies.getCookies().stream().map(cookie -> cookie.getName()).sorted().toList();
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
  }

  private static String valueOf(Header header) {
    return header == null ? null : header.getValue();
  }

  @Override
  public void close() {
    client.getConnectionManager().shutdown();
  }
}
