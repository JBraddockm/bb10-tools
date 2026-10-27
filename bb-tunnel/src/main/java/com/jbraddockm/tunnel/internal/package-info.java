/**
 * Shared, dependency-free helpers used across the tunnel packages: {@link
 * com.jbraddockm.tunnel.internal.Hex} (byte/hex/IP conversions and the internet checksum) and
 * {@link com.jbraddockm.tunnel.internal.PemUtil} (loading DER blocks from PEM). No tunnel API of
 * its own — imported by {@code com.jbraddockm.tunnel} where needed.
 */
package com.jbraddockm.tunnel.internal;
