package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.cgi.DeviceCommand;

/**
 * Asks the device to sign a certificate request: a POST to {@code
 * login.cgi?request_version=3&cgiauth=1} with the request as PEM in the body, as {@code
 * application/octet-stream}, over the session that the password login made. The device answers with
 * the certificate as PEM and nothing else **[verified]**; an error comes as XML in {@code CgiAuth},
 * and a body of another content type is refused with error 260.
 *
 * <p>It is never repeated after a dropped connection, for the device may have counted the
 * certificate.
 *
 * @param csrPem the certificate request as PEM
 */
public record GetAuthenticationCertificate(String csrPem)
    implements DeviceCommand<AuthenticationAnswer> {

  /** The only content type that the device takes for the request. */
  static final String CONTENT_TYPE = "application/octet-stream";

  /**
   * Checks the components.
   *
   * @throws InvalidArgumentException if the request is blank
   */
  public GetAuthenticationCertificate {
    if (csrPem == null || csrPem.isBlank()) {
      throw new InvalidArgumentException("csr", "is required");
    }
  }

  @Override
  public Conversation<AuthenticationAnswer> conversation() {
    var request =
        CgiRequest.Body.of(Endpoint.LOGIN, csrPem, CONTENT_TYPE)
            .with(QueryParameter.REQUEST_VERSION, "3")
            .with(QueryParameter.CGI_AUTH, "1")
            .describedAs("Asking the device to sign a certificate request");
    return new AuthenticationExchange(request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
