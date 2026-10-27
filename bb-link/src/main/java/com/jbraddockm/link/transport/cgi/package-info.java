/**
 * The typed vocabulary of the device's CGI interface: endpoints, form fields, requests, responses
 * and the conversations that turn a command into a series of exchanges.
 *
 * <p>Nothing here performs input or output. A conversation is a state machine that is given each
 * response and answers with the next request, so it can be tested without a device.
 */
package com.jbraddockm.link.transport.cgi;
