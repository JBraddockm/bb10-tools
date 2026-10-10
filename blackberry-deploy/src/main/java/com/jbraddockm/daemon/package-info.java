/**
 * The daemon: the {@code blackberry-deploy} commands as a line-delimited JSON protocol over
 * standard input and output, so that any program can drive devices without parsing the tool's text.
 *
 * <p>This package is an adapter. It decodes requests into {@link
 * com.jbraddockm.device.DeviceCommand commands}, runs them on {@link
 * com.jbraddockm.device.DeviceSession sessions}, and writes the outcomes as events. The protocol
 * itself is specified in {@code PROTOCOL.md}. Only the entry points are public; everything else is
 * an implementation detail.
 */
package com.jbraddockm.daemon;
