/**
 * Contact photos: the one part of {@code pimsync.cgi} whose body is binary rather than JSON. A
 * photo {@code get} streams an image to a file and a photo {@code put} sends one as a multipart
 * upload, so these commands use their own transport shapes and their own result, {@link
 * com.jbraddockm.link.endpoint.pimsync.photo.PimPhoto}, rather than the text {@code PimAnswer} of
 * the other PIM commands.
 */
package com.jbraddockm.link.endpoint.pimsync.photo;
