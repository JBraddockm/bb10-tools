/**
 * Backing the device up and restoring it, over {@code backup.cgi}.
 *
 * <p>A backup selects categories, waits while the device prepares each archive, and streams them
 * into a {@code .bbb} file ({@link com.jbraddockm.link.endpoint.backup.DeviceBackup}); a restore
 * uploads those archives back and the device resets to apply them ({@link
 * com.jbraddockm.link.endpoint.backup.DeviceRestore}). The device does all the encryption: bb-link
 * stores and returns its archives verbatim and never looks inside them. The device must already
 * hold a backup-and-restore key in its session for the data to move; supplying that key is outside
 * bb-link.
 */
package com.jbraddockm.link.endpoint.backup;
