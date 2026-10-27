package com.jbraddockm.link.transport.session;

import java.io.IOException;

/**
 * Brings up (or joins) the Wi-Fi tunnel for a device and returns a hold on it. A {@link
 * DeviceSession} configured with one resolves it on {@code establishTunnel} — before the first
 * command — and then connects over the tunnel's ULA instead of the address the user gave. Kept as
 * an interface so the session is testable without a broker; the production implementation is {@link
 * BrokerTunnelConnector}.
 */
@FunctionalInterface
interface TunnelConnector {

  /**
   * Brings up or joins the tunnel.
   *
   * @return the hold on the live tunnel
   * @throws IOException if the tunnel cannot be brought up (e.g. the broker is unreachable)
   */
  TunnelBinding connect() throws IOException;
}
