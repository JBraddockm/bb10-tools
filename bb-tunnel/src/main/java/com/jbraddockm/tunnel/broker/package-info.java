/**
 * The tunnel-broker daemon: a privileged, long-lived process that owns every bp2p tunnel's
 * lifecycle and lets unprivileged consumers (bb-link, Blend) share one tunnel per device over a
 * local Unix-domain socket.
 *
 * <p>{@link com.jbraddockm.tunnel.broker.TunnelBroker} is the shared, transport-agnostic core
 * (leases, identity, events over {@link com.jbraddockm.tunnel.TunnelManager}); {@link
 * com.jbraddockm.tunnel.broker.BrokerServer} serves it on a socket; {@link
 * com.jbraddockm.tunnel.broker.Tunneld} is the entry point. The client lives in bb-core ({@code
 * com.jbraddockm.core.tunnel}). See {@code analysis/TUNNEL-BROKER-DAEMON-PLAN.md}.
 */
package com.jbraddockm.tunnel.broker;
