/**
 * Tooling around the {@code blackberry-deploy} command line tool for BlackBerry 10 devices.
 *
 * <p>The code is organised by feature:
 *
 * <ul>
 *   <li>{@link com.jbraddockm.output} turns the text the tool prints into typed records. It has no
 *       dependency on the tool itself and is meant to be reused by other BlackBerry tooling.
 *   <li>{@link com.jbraddockm.device} runs commands against a device, keeps the login session and
 *       classifies failures.
 *   <li>{@link com.jbraddockm.daemon} exposes the above to other programs as a line-delimited JSON
 *       protocol over standard input and output.
 *   <li>{@code com.jbraddockm.internal} contains helpers that are not part of any API.
 * </ul>
 *
 * <p>This package itself holds the small shared kernel (error vocabulary) and the application entry
 * point.
 */
package com.jbraddockm;
