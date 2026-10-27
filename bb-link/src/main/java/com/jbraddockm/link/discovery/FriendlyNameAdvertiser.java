package com.jbraddockm.link.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

/**
 * Advertises this workstation's bp2p <b>Friendly</b> service over mDNS so that a paired BlackBerry 10
 * device learns our name and shows it in <i>Settings &gt; Device Connections</i> (instead of the
 * {@code Desktop-XXXX} fallback). This is the piece the real BlackBerry Link / PeerManager does and
 * that bb-link was missing: pairing only enrols the certificate; the display name reaches the device
 * only via this DNS-SD advert.
 *
 * <p>It registers a service of type {@link FriendlyIdentity#SERVICE_TYPE} with the instance name
 * {@code Friendly_<seed>_<deviceHash>} (see {@link FriendlyIdentity}) and a TXT record carrying
 * {@code fname=<name>}. The device resolves it, reads {@code fname}, matches the {@code deviceHash}
 * to our paired certificate, and writes {@code /pps/services/bp2p/devices/<id>/devicename}.
 *
 * <p>Keep an instance alive for as long as the device should see the name (the device re-browses and
 * the official stack re-publishes periodically); {@link #close()} withdraws it.
 *
 * <p><b>Wiring:</b> start one after a pairing has persisted (and on daemon start while a pairing
 * exists), passing the workstation name — reuse {@code pairing.ClientNames.localWorkstation()} — and
 * the {@code peerIdentifier} we registered at pairing (the {@code p2p_client_id} the device stored
 * for our certificate). There is no natural hook in {@code LinkService} yet; add the call at the
 * daemon bootstrap / pairing-completion site. <b>Not yet live-verified for a workstation advert:</b>
 * the fold ({@link FriendlyIdentity#deviceHash}) is confirmed against a device's own advert, but which
 * exact identifier string the device stores for a <i>workstation</i> peer should be confirmed on the
 * device before relying on it.
 */
public final class FriendlyNameAdvertiser implements AutoCloseable {

  private static final System.Logger LOG = System.getLogger(FriendlyNameAdvertiser.class.getName());

  /**
   * Nominal SRV port. The device's observer only needs the resolve + TXT, but a DNS-SD service still
   * needs a port; this value is not otherwise meaningful.
   */
  private static final int NOMINAL_PORT = 4455;

  private JmDNS jmdns;
  private ServiceInfo service;

  /**
   * Registers the Friendly service and begins advertising. Blocks only for the initial mDNS
   * registration.
   *
   * @param friendlyName the name to show on the device (the TXT {@code fname})
   * @param peerIdentifier our device identifier as registered at pairing — the string the device
   *     stored for our certificate, which it re-hashes to match our advertised {@code deviceHash}
   *     (see {@link FriendlyIdentity#deviceHash})
   * @throws IOException if mDNS registration fails
   */
  public void start(String friendlyName, String peerIdentifier) throws IOException {
    byte[] seed = FriendlyIdentity.newSeed();
    byte[] deviceHash = FriendlyIdentity.deviceHash(peerIdentifier, seed);
    String instance = FriendlyIdentity.instanceName(seed, deviceHash);

    Map<String, String> txt = new HashMap<>();
    txt.put("txtvers", "1");
    txt.put("protovers", "2");
    txt.put(FriendlyIdentity.TXT_FNAME, friendlyName);

    jmdns = JmDNS.create(InetAddress.getLocalHost());
    service =
        ServiceInfo.create(FriendlyIdentity.SERVICE_TYPE, instance, NOMINAL_PORT, 0, 0, true, txt);
    jmdns.registerService(service);
    LOG.log(System.Logger.Level.INFO, () -> "Advertising bp2p Friendly service " + instance
        + " (" + FriendlyIdentity.TXT_FNAME + "=" + friendlyName + ")");
  }

  /**
   * Best-effort {@link #start}: returns the running advertiser, or {@link Optional#empty()} (with a
   * warning) if it cannot start. Never throws — the friendly name is a courtesy, so nothing it does
   * may break the connection it rides along with. This catches more than the {@link IOException}
   * {@link #start} declares: in a native image mDNS can fail with a runtime exception or a linkage
   * error, and those must be swallowed too.
   */
  public static Optional<FriendlyNameAdvertiser> tryStart(String friendlyName, String peerIdentifier) {
    FriendlyNameAdvertiser advertiser = new FriendlyNameAdvertiser();
    try {
      advertiser.start(friendlyName, peerIdentifier);
      return Optional.of(advertiser);
    } catch (Exception | LinkageError e) {
      LOG.log(System.Logger.Level.WARNING, "Friendly-name advertising failed to start", e);
    }
    advertiser.close();
    return Optional.empty();
  }

  /** Withdraws the advert and releases the mDNS responder. Safe to call more than once. */
  @Override
  public void close() {
    JmDNS local = jmdns;
    jmdns = null;
    service = null;
    if (local == null) return;
    try {
      local.unregisterAllServices();
      local.close();
    } catch (IOException e) {
      LOG.log(System.Logger.Level.WARNING, "Failed to stop the Friendly-name advertiser", e);
    }
  }
}
