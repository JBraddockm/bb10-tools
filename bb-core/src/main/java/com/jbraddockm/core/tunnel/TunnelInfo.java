package com.jbraddockm.core.tunnel;

import java.util.List;

/**
 * One tunnel the broker holds, as reported by {@link TunnelBrokerClient#status}.
 *
 * @param host the transport address the tunnel was opened with
 * @param state the lifecycle state (currently always {@code "UP"})
 * @param ula the device's tunnel ULA
 * @param innerIpv4 the device's inner IPv4 (the SMB host)
 * @param guid the device GUID if known, else {@code null}
 * @param pin the device PIN if known, else {@code null}
 * @param refs how many consumers hold this device's tunnel
 * @param consumers the distinct consumer labels holding it
 */
public record TunnelInfo(
    String host,
    String state,
    String ula,
    String innerIpv4,
    String guid,
    String pin,
    int refs,
    List<String> consumers) {}
