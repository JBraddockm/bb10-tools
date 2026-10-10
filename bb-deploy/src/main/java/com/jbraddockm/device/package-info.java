/**
 * Running {@code bb-deploy} commands against a device.
 *
 * <p>A {@link com.jbraddockm.device.DeviceSession} owns one login to one device. Commands are
 * described by the sealed {@link com.jbraddockm.device.DeviceCommand} hierarchy; running one yields
 * a {@link com.jbraddockm.device.CommandOutcome} that is either a typed result or a classified
 * failure. The session logs in once and reuses the login for later commands, renews it
 * transparently when the device forgets it, and retries a dropped connection only for commands that
 * are safe to repeat.
 *
 * <p>Nothing here knows about the JSON protocol: that is an adapter in the {@code daemon} package.
 */
package com.jbraddockm.device;
