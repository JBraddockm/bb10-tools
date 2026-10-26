package com.jbraddockm.tunnel;

import java.util.BitSet;

/**
 * Hands out distinct inner-IPv4 pairs so several paired devices can hold <b>concurrent</b> tunnels
 * without colliding. The device binds whatever valid {@code 169.254.0.0/16} address we send it
 * (live-verified — see {@code analysis/TUNNEL-MULTIDEVICE-MODEL.md}), so the pair is ours to choose
 * per session; this pool is how we choose it.
 *
 * <p>Each pair takes a distinct third octet {@code k}: device {@code 169.254.k.1}, pc {@code
 * 169.254.k.2}. {@code k = 0} ({@code 169.254.0.0/24} — the USB NCM interface and RFC-3927's
 * reserved first {@code /24}) and {@code k = 255} (RFC-3927's reserved last {@code /24}) are never
 * handed out. {@code k = 1} ({@code 169.254.1.1/.2}) is held back for {@link #allocateStable()} so
 * a single-device or external-app mount can always keep a <b>constant</b> {@code
 * smb://169.254.1.1/} address — only the password rotates — while {@link #allocate()} gives
 * concurrent sessions {@code k = 2, 3, …}.
 *
 * <p>Thread-safe; the session manager holds one pool and {@link #release(InnerIpv4) releases} a
 * pair when its tunnel tears down.
 */
public final class InnerAddressPool {

  /**
   * The reserved stable third octet — {@code 169.254.1.1/.2}; granted only via {@link
   * #allocateStable()}.
   */
  private static final int STABLE_K = 1;

  private static final int MIN_K = 2;
  private static final int MAX_K = 254;

  private final BitSet used = new BitSet(256);
  private boolean stableTaken;

  public InnerAddressPool() {
    used.set(0); // 169.254.0.0/24 — USB NCM + RFC-3927 reserved first /24
    used.set(STABLE_K); // held back for allocateStable()
    used.set(255); // 169.254.255.0/24 — RFC-3927 reserved last /24
  }

  private static InnerIpv4 pair(int k) {
    return new InnerIpv4("169.254." + k + ".2", "169.254." + k + ".1");
  }

  private static int thirdOctet(String ipv4) {
    return Integer.parseInt(ipv4.split("\\.")[2]);
  }

  /**
   * The preferred stable pair {@code 169.254.1.1/.2}, for a single-device or external-app mount
   * that wants a constant SMB address. Throws if it is already in use.
   */
  public synchronized InnerIpv4 allocateStable() {
    if (stableTaken) {
      throw new IllegalStateException("stable inner-IPv4 (169.254.1.x) already in use");
    }
    stableTaken = true;
    return pair(STABLE_K);
  }

  /** The next free pair ({@code k = 2…254}). Throws when all 253 are in use. */
  public synchronized InnerIpv4 allocate() {
    int k = used.nextClearBit(MIN_K);
    if (k > MAX_K) {
      throw new IllegalStateException("inner-IPv4 pool exhausted (253 concurrent sessions)");
    }
    used.set(k);
    return pair(k);
  }

  /** Returns a pair to the pool; ignored if it was not one this pool handed out. */
  public synchronized void release(InnerIpv4 pair) {
    int k = thirdOctet(pair.deviceIpv4());
    if (k == STABLE_K) {
      stableTaken = false;
    } else if (k >= MIN_K && k <= MAX_K) {
      used.clear(k);
    }
  }
}
