package com.jbraddockm.link.session;

import com.jbraddockm.link.cgi.CgiResponse;
import java.util.List;

/** Is told about every exchange with the device. Used to record traces. */
@FunctionalInterface
public interface ExchangeObserver {

  /** An observer that ignores everything. */
  ExchangeObserver NONE = (requestLine, requestBody, cookieNames, response) -> {};

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
}
