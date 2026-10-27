/**
 * The Wi-Fi tunnel a session can run over: the {@link
 * com.jbraddockm.link.transport.tunnel.TunnelConnector} that brings one up and the {@link
 * com.jbraddockm.link.transport.tunnel.TunnelBinding} that holds it, with {@link
 * com.jbraddockm.link.transport.tunnel.BrokerTunnelConnector} driving the bb-core tunnel broker. A
 * session depends on this package; this package does not depend back on the session.
 */
package com.jbraddockm.link.transport.tunnel;
