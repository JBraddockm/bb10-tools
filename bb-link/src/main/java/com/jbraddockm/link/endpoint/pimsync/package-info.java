/**
 * Reading the personal information the device keeps (contacts, calendars and events) through {@code
 * pimsync.cgi}.
 *
 * <p>The device answers in JSON-like text whose shape is not known beyond its status. bb-link reads
 * the status, to tell a rejected request from an accepted one, and hands the rest over untouched.
 * Only commands that read are implemented. Photos, which the device returns as image bytes, are
 * not.
 */
package com.jbraddockm.link.endpoint.pimsync;
