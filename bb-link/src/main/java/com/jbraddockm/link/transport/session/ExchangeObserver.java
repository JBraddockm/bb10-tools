package com.jbraddockm.link.transport.session;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import java.io.IOException;
import java.util.List;

/** Is told about every exchange with the device. Used to record traces. */
@FunctionalInterface
public interface ExchangeObserver {

  /** An observer that ignores everything. */
  ExchangeObserver NONE = (_, _, _, _) -> {};

  /**
   * Called after the device answered.
   *
   * @param requestLine the method and path of the request, including any query
   * @param requestBody the request body; empty for a GET
   * @param cookieNames the names of the cookies that were in the session's cookie jar when the
   *     request was sent; never their values
   * @param response the device's answer
   */
  void onExchange(
      String requestLine, String requestBody, List<String> cookieNames, CgiResponse response);

  /**
   * Called when the request was sent but the device closed the connection without answering.
   * Implementations that do not record failures may leave this as its default no-op.
   *
   * @param requestLine the method and path of the request, including any query
   * @param requestBody the request body; empty for a GET
   * @param cookieNames the names of the cookies that were in the session's cookie jar when the
   *     request was sent; never their values
   * @param failure what the transport layer received instead of a response
   */
  default void onFailedExchange(
      String requestLine, String requestBody, List<String> cookieNames, IOException failure) {}
}
