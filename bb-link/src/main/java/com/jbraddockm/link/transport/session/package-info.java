/**
 * The connection to one device: {@link com.jbraddockm.link.transport.session.DeviceSession} holds an
 * authenticated session and renews it, {@link
 * com.jbraddockm.link.transport.session.ConversationRunner} drives one exchange over it, and {@link
 * com.jbraddockm.link.transport.session.StaleSession} recognises when the device has forgotten it.
 * The login itself lives in {@code transport.login}, which this package drives.
 */
package com.jbraddockm.link.transport.session;
