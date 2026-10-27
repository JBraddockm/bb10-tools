/**
 * The login to a device and the reading of its outcome: {@link
 * com.jbraddockm.link.transport.login.Login} runs the challenge-response exchange, {@link
 * com.jbraddockm.link.transport.login.LoginDocument} parses its answer, {@link
 * com.jbraddockm.link.transport.login.SmbCredentials} and {@link
 * com.jbraddockm.link.transport.login.LoginOnly} carry its result, and {@link
 * com.jbraddockm.link.transport.login.FailureClassifier} maps a failed exchange to an error. The
 * login speaks over the CGI transport; the session depends on this package, not the other way round.
 */
package com.jbraddockm.link.transport.login;
