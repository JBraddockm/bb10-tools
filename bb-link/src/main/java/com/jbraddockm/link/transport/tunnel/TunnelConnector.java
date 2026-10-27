package com.jbraddockm.link.transport.tunnel;

import java.io.IOException;

/**
 * Brings up (or joins) the Wi-Fi tunnel for a device and returns a hold on it, resolved before the
 * first command so the session then connects over the tunnel's ULA instead of the address the user
 * gave. The production implementation is {@link BrokerTunnelConnector}.
 */
@FunctionalInterface
public interface TunnelConnector {

  /**
   * Brings up or joins the tunnel.
   *
   * @return the hold on the live tunnel
   * @throws IOException if the tunnel cannot be brought up (e.g. the broker is unreachable)
   */
  TunnelBinding connect() throws IOException;
}
