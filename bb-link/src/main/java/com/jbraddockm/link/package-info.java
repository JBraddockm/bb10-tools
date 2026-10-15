/**
 * bb-link: a daemon that manages BlackBerry 10 devices through the protocol of BlackBerry Link, as
 * documented by the open source application Sachesi.
 *
 * <p>The program is a native-image executable that serves line-delimited JSON on standard input and
 * output. Its layering mirrors bb-deploy: {@code daemon} speaks the protocol, the feature packages
 * ({@code properties}, {@code actions}, {@code discovery}) describe what a command asks and how the
 * answer is read, {@code session} owns the login and the HTTP connection, {@code cgi} is the typed
 * vocabulary of the device's web interface, and {@code internal} holds helpers.
 */
package com.jbraddockm.link;
