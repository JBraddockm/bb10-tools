package com.jbraddockm.tunnel;

import java.util.function.BiConsumer;

/**
 * The shared tunnel service a consumer depends on: acquire a device's tunnel, and be told when one
 * drops. Implemented by {@link TunnelManager}; kept as an interface so a consumer (the broker) is
 * testable without a device or {@code sudo}.
 */
public interface Tunnels {

  /**
   * The tunnel for the device reached at {@code host}, opened if it is not already up or the
   * existing one with its reference count raised. {@code stable} pins the constant {@code
   * 169.254.1.1} SMB address; otherwise a free inner-IPv4 pair is allocated.
   *
   * @return a hold on the tunnel, carrying the coordinates to connect with
   * @throws Exception if the tunnel cannot be opened
   */
  Lease acquire(String host, boolean stable) throws Exception;

  /** Whether a tunnel is currently held for {@code host}. */
  boolean isOpen(String host);

  /**
   * Registers the listener told {@code (host, cause)} whenever a tunnel drops on its own (the link
   * failed, not a {@link Lease#close}). Set once before use.
   */
  void onDrop(BiConsumer<String, Throwable> listener);

  /**
   * A hold on a device's tunnel: the coordinates a consumer connects with, and a ref-counted close.
   */
  interface Lease extends AutoCloseable {

    /** The device's tunnel ULA — the host a consumer connects to. */
    String deviceUla();

    /** The device's inner IPv4 — the SMB host. */
    String deviceIpv4();

    /** The transport address the tunnel was opened with. */
    String host();

    /** Releases this hold; the tunnel comes down when the last lease closes. Idempotent. */
    @Override
    void close();
  }
}
