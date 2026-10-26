package com.jbraddockm.link.session;

/**
 * A hold on a device's Wi-Fi tunnel for the lifetime of a {@link DeviceSession}. The session
 * connects over {@link #ula()} and reports {@link #smbHost()} as the share address; {@link
 * #isDown()} turns true once the tunnel has dropped, so the session can fail further commands with
 * a clear "tunnel down" rather than hang. Closing it releases the hold (the broker brings the
 * shared tunnel down when the last consumer releases).
 */
public interface TunnelBinding extends AutoCloseable {

  /** The device's tunnel ULA — the host the session actually connects to. */
  String ula();

  /** The device's inner IPv4 on the tunnel — the SMB share host reported to the client. */
  String smbHost();

  /** Whether the tunnel has dropped on its own since it was brought up. */
  boolean isDown();

  /** Releases the hold. Must not throw. */
  @Override
  void close();
}
