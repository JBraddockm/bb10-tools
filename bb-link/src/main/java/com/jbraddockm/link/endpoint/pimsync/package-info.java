/**
 * The personal-information sync of the device (contacts, calendars and events) through {@code
 * pimsync.cgi}.
 *
 * <p>This root package holds what every command shares: the conversation with the device, the
 * status it answers with, the kinds of items ({@link
 * com.jbraddockm.link.endpoint.pimsync.PimService}) and the result ({@link
 * com.jbraddockm.link.endpoint.pimsync.PimAnswer}). The commands themselves live in sub-packages by
 * role: {@code account} for the accounts a workstation is registered as, {@code item} for the
 * per-service reads and writes, and {@code photo} for contact photos, which the device returns as
 * image bytes.
 *
 * <p>The device answers in JSON-like text whose shape is not known beyond its status. bb-link reads
 * the status, to tell a rejected request from an accepted one, and hands the rest over untouched: a
 * clear API of the pimsync primitives, leaving the sync logic itself to the client.
 */
package com.jbraddockm.link.endpoint.pimsync;
