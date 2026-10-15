/**
 * The line-delimited JSON server that every daemon runs.
 *
 * <p>A tool supplies a {@link com.jbraddockm.core.daemon.Service}; this package owns the protocol:
 * the {@code hello} event, request decoding, sessions with ordered queues, exactly one {@code done}
 * per request, and an orderly drain when the input ends.
 */
package com.jbraddockm.core.daemon;
