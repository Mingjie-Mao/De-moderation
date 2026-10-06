package com.campusguard.comment;

import java.util.List;

/**
 * A bounded slice of a thread, including roots and replies in creation order.
 *
 * <p>Each item carries its parent id. A page may end between parent and child,
 * allowing clients to assemble the tree across pages without a single root's
 * popularity making the server response unbounded.
 *
 * <p>Same shape as the feed's page on purpose. A client that already knows how to
 * follow {@code nextCursor} through a forum does not need to learn a second idea
 * to follow one through a thread.
 *
 * @param hasMore established by reading one comment past the page and discarding it
 * @param nextCursor null on the last page
 */
public record CommentPage(List<CommentResponse> items, boolean hasMore, String nextCursor) {
}
