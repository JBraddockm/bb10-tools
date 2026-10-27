package com.jbraddockm.link.discovery;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Builds the bp2p <b>Friendly</b> DNS-SD service instance name the device expects, including the
 * seed-salted identity token inside it.
 *
 * <p>From live mDNS captures, a peer advertises its friendly name as a service of type
 * {@code _friendly._sub._bp2p._tcp.local.} whose instance name is
 * <pre>  Friendly_&lt;SEED 16-hex&gt;_&lt;DEVICEHASH 16-hex&gt;</pre>
 * (two 8-byte tokens, upper-case hex). The device's {@code FriendlyNameObserver} parses it into
 * {@code name / random / deviceHash}, then recomputes the token per paired peer
 * ({@code TrustManagerQuery::queryForCertByDeviceHashAndSeed}) to map it to a trusted peer; the
 * human-readable name travels separately in the TXT record under the key {@code fname}.
 *
 * <h2>The token fold (recovered from {@code libSapphireUtilities}, {@code ServiceName::makeServiceName})</h2>
 *
 * <pre>
 *   seed       = 8 random bytes                       (RAND_bytes)
 *   deviceHash = SHA1( seed || ASCII(peerIdentifier) )[12:20]   // last 8 bytes of the digest
 *   instance   = "Friendly_" + HEXUP(seed) + "_" + HEXUP(deviceHash)
 * </pre>
 *
 * where {@code peerIdentifier} is the advertiser's own device identifier — for a BlackBerry device,
 * its 8-hex uid (e.g. {@code "2C1E7583"}, from {@code getDeviceIdentifier()}); for a workstation, the
 * identifier it registered at pairing. The device recomputes the same SHA-1 over the identifier it has
 * stored for each paired peer and matches the window, so our advertised {@code peerIdentifier} must be
 * exactly the string the device stored for us at pairing.
 *
 * <p><b>Verified</b> against device {@code C239E313}'s own advert:
 * {@code SHA1(33C9D3A93B9DD79F || "2C1E7583")[12:20] = 9B2770A11C414366}, reproducing the captured
 * instance {@code Friendly_33C9D3A93B9DD79F_9B2770A11C414366}.
 */
final class FriendlyIdentity {

  /** DNS-SD type (with the {@code _friendly} subtype) that the device browses for. */
  static final String SERVICE_TYPE = "_friendly._sub._bp2p._tcp.local.";

  /** The first instance-name token. */
  static final String INSTANCE_PREFIX = "Friendly";

  /** TXT key carrying the displayed name. */
  static final String TXT_FNAME = "fname";

  private static final HexFormat UPPER = HexFormat.of().withUpperCase();
  private static final SecureRandom RANDOM = new SecureRandom();

  private FriendlyIdentity() {}

  /** A fresh 8-byte seed (the {@code random} token, published so the device can recompute the hash). */
  static byte[] newSeed() {
    byte[] seed = new byte[8];
    RANDOM.nextBytes(seed);
    return seed;
  }

  /** The instance name {@code Friendly_<SEED>_<DEVICEHASH>} (upper-case hex, as seen on the wire). */
  static String instanceName(byte[] seed, byte[] deviceHash) {
    return INSTANCE_PREFIX + "_" + UPPER.formatHex(seed) + "_" + UPPER.formatHex(deviceHash);
  }

  /**
   * Derives the 8-byte {@code deviceHash} token: {@code SHA1(seed || ASCII(peerIdentifier))[12:20]}.
   * The device recomputes the same over the identifier it stored for our paired cert and matches it.
   *
   * @param peerIdentifier our device identifier as registered at pairing (ASCII; the string the
   *     device stored for our certificate — for a BlackBerry device this is its 8-hex uid)
   * @param seed the 8-byte seed that will be published in the instance name
   * @return the 8-byte device-hash token (bytes [12:20] of the SHA-1 digest)
   */
  static byte[] deviceHash(String peerIdentifier, byte[] seed) {
    MessageDigest sha1 = sha1();
    sha1.update(seed);
    sha1.update(peerIdentifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    byte[] digest = sha1.digest();
    byte[] token = new byte[8];
    System.arraycopy(digest, 12, token, 0, 8); // last 8 bytes of SHA-1
    return token;
  }

  private static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e); // every JRE ships it
    }
  }
}
