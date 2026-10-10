package com.jbraddockm.daemon;

/**
 * A decoded request.
 *
 * @param id the client's identifier, echoed in every reply
 * @param method the method name as sent
 * @param params the parameters, empty if none were sent
 */
record Request(RequestId id, String method, RequestParams params) {}
