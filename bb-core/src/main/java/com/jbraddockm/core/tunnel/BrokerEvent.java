package com.jbraddockm.core.tunnel;

/**
 * An unsolicited event the broker pushes to a connection holding the device: {@code dropped} (the
 * tunnel failed on its own — a consumer should report the device as down) or {@code identity}
 * (another consumer reported the device's GUID/PIN). The {@code hello} greeting arrives as one too.
 *
 * @param type {@code "dropped"}, {@code "identity"} or {@code "hello"}
 * @param host the device's transport address (absent on {@code hello})
 * @param guid the device GUID (on {@code identity})
 * @param pin the device PIN (on {@code identity})
 * @param cause the drop cause text (on {@code dropped}), or {@code null}
 */
public record BrokerEvent(String type, String host, String guid, String pin, String cause) {}
