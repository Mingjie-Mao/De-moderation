package com.campusguard.comment;

import com.campusguard.common.AuthorView;
import com.campusguard.common.ConflictException;
import com.campusguard.common.ContentRateLimitProperties;
import com.campusguard.common.NotFoundException;
import com.campusguard.common.TooManyRequestsException;
import com.campusguard.post.Post;
import com.campusguard.post.PostRepository;
import com.campusguard.user.User;
import com.campusguard.user.UserRepository;
import com.campusguard.user.UserRole;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import com.campusguard.common.PageCursor;
import com.campusguard.media.MediaObject;
import com.campusguard.media.MediaService;
import com.campusguard.media.MediaUrls;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ContentRateLimitProperties rateLimits;
    private final MediaService mediaService;
    private final com.campusguard.community.CommunityNotifications notifications;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ContentRateLimitProperties rateLimits,
            MediaService mediaService, com.campusguard.community.CommunityNotifications notifications) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.rateLimits = rateLimits;
        this.mediaService = mediaService; this.notifications=notifications;
    }

    @Transactional
    public CommentResponse create(UUID postId, UUID authorId, CreateCommentRequest request) {
        requireWithinRateLimit(authorId);

        Post post = postRepository
                .findLiveById(postId)
                .orElseThrow(() -> new NotFoundException("No post with id " + postId));

        User author = userRepository
                .findById(authorId)
                .orElseThrow(() -> new NotFoundException("No user with id " + authorId));

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = commentRepository
                    .findLiveById(request.parentCommentId())
                    .orElseThrow(() -> new NotFoundException(
                            "No comment with id " + request.parentCommentId()));

            // Without this check a reply could be grafted onto a thread on a
            // different post, producing a comment that is unreachable from the
            // post it belongs to and orphaned under the one it points at.
            // The check constraint is the guarantee; this is here so the answer
            // is a sentence a person can act on rather than a constraint
            // violation surfacing as a 500.
            if (parent.getDepth() >= Comment.MAX_DEPTH) {
                throw new ConflictException(
                        "This thread is already nested %d replies deep, which is as far as it goes. "
                                .formatted(Comment.MAX_DEPTH)
                                + "Reply further up the thread instead.");
            }

            if (!parent.getPost().getId().equals(postId)) {
                throw new NotFoundException(
                        "Comment " + parent.getId() + " does not belong to post " + postId);
            }
        }

        // See PostService#create: the flush is what populates createdAt before
        // the response is built.
        MediaObject media = request.mediaId() == null ? null : mediaService.requireOwned(request.mediaId(), authorId);
        Comment saved =
                commentRepository.saveAndFlush(new Comment(
                        post, parent, author, request.body() == null ? "" : request.body(), media));

        notifications.send(authorId,post.getAuthor().getId(),"COMMENT","commented on your post","POST",postId);
        if(parent!=null && !parent.getAuthor().getId().equals(post.getAuthor().getId()))
            notifications.send(authorId,parent.getAuthor().getId(),"COMMENT","replied to your comment","POST",postId);
        var mentions=java.util.regex.Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,50})").matcher(saved.getBody());
        var mentioned=new java.util.HashSet<UUID>();
        while(mentions.find()) userRepository.findByUsername(mentions.group(1)).ifPresent(u->{
            if(mentioned.add(u.getId())) notifications.send(authorId,u.getId(),"MENTION","mentioned you in a comment","POST",postId);
        });
        return new CommentResponse(
                saved.getId(),
                parent == null ? null : parent.getId(),
                AuthorView.of(author),
                saved.getBody(),
                saved.getMedia() == null ? null : MediaUrls.publicUrl(saved.getMedia().getId()),
                saved.getCreatedAt(),
                List.of());
    }

    @Transactional
    public CommentResponse update(
            UUID postId, UUID commentId, UUID authorId, UpdateCommentRequest request) {
        Comment comment = requireCommentOnPost(postId, commentId);
        if (!comment.getAuthor().getId().equals(authorId)) {
            throw new AccessDeniedException("Only the author can edit this comment.");
        }

        MediaObject media = request.mediaId() == null
                ? null
                : mediaService.requireOwned(request.mediaId(), authorId);
        comment.update(request.body() == null ? "" : request.body(), media);
        return toResponse(comment);
    }

    @Transactional
    public void delete(UUID postId, UUID commentId, UUID actorId) {
        Comment comment = requireCommentOnPost(postId, commentId);
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("No user with id " + actorId));
        if (!comment.getAuthor().getId().equals(actorId) && actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException(
                    "Only the author or an administrator can delete this comment.");
        }
        comment.softDelete(Instant.now());
    }

    private Comment requireCommentOnPost(UUID postId, UUID commentId) {
        Comment comment = commentRepository.findLiveById(commentId)
                .orElseThrow(() -> new NotFoundException("No comment with id " + commentId));
        if (!comment.getPost().getId().equals(postId)) {
            throw new NotFoundException(
                    "Comment " + commentId + " does not belong to post " + postId);
        }
        return comment;
    }

    /**
     * One bounded page of comments in creation order, roots and replies alike.
     *
     * <p>Paging only roots left one popular root free to pull the entire reply
     * table into memory. A page can now end between a parent and its child;
     * {@code parentCommentId} lets clients assemble the tree across pages.
     */
    @Transactional(readOnly = true)
    public CommentPage thread(UUID postId, String cursor, int size) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new NotFoundException("No post with id " + postId);
        }

        PageCursor from = cursor == null || cursor.isBlank() ? null : PageCursor.decode(cursor);

        // One past the page, so "is there more" costs a row rather than a count.
        Pageable window = PageRequest.of(0, size + 1);
        List<Comment> rows = from == null
                ? commentRepository.findThreadFirstPage(postId, window)
                : commentRepository.findThreadAfter(postId, from.createdAt(), from.id(), window);

        boolean hasMore = rows.size() > size;
        List<Comment> page = hasMore ? rows.subList(0, size) : rows;
        List<CommentResponse> items = page.stream().map(this::toResponse).toList();

        String next = hasMore && !page.isEmpty()
                ? new PageCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
                : null;

        return new CommentPage(items, hasMore, next);
    }

    private CommentResponse toResponse(Comment comment) {
        Comment parent = comment.getParent();

        return new CommentResponse(
                comment.getId(),
                parent == null ? null : parent.getId(),
                AuthorView.of(comment.getAuthor()),
                comment.getBody(),
                comment.getMedia() == null ? null : MediaUrls.publicUrl(comment.getMedia().getId()),
                comment.getCreatedAt(),
                List.of());
    }

    /**
     * Higher than the post limit, because replying is the ordinary way to use a
     * forum and starting threads is not. Same purpose: registration is open, so
     * being signed in was never a brake on a script.
     */
    private void requireWithinRateLimit(UUID authorId) {
        Instant since = Instant.now().minus(rateLimits.window());
        long recent = commentRepository.countByAuthorIdAndCreatedAtAfter(authorId, since);

        if (recent >= rateLimits.commentsPerUser()) {
            throw new TooManyRequestsException(
                    "You have commented %d times in the last %s. Try again later."
                            .formatted(recent, rateLimits.window()));
        }
    }
}
