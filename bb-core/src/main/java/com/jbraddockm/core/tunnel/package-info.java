/**
 * Client of the tunnel-broker daemon: {@link com.jbraddockm.core.tunnel.TunnelBrokerClient} plus
 * its data types. A consumer (bb-link, Blend) uses it to share a device's bp2p tunnel — {@code
 * acquire}/{@code release} a hold, {@code identify} a device, read {@code status}, and receive
 * {@code dropped}/{@code identity} events — without owning the tunnel's lifecycle. The daemon lives
 * in bb-tunnel ({@code com.jbraddockm.tunnel.broker}); the protocol is line-delimited JSON over a
 * Unix-domain socket. See {@code analysis/TUNNEL-BROKER-DAEMON-PLAN.md}.
 */
package com.jbraddockm.core.tunnel;
