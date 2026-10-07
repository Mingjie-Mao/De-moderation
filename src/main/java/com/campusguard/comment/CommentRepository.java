package com.campusguard.comment;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    interface Preview {
        UUID getId();
        String getBody();
        UUID getMediaId();
    }

    @Query("select c.id as id, c.body as body, m.id as mediaId from Comment c left join c.media m where c.id in :ids")
    List<Preview> findModerationPreviews(@Param("ids") List<UUID> ids);

    /** Every returned row is bounded by the page size, including replies. */
    @Query("""
            select c from Comment c
            join fetch c.author
            left join fetch c.media
            where c.post.id = :postId and c.deletedAt is null
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findThreadFirstPage(@Param("postId") UUID postId, Pageable pageable);

    /** Seek by creation time and id so ties cannot duplicate or skip a comment. */
    @Query("""
            select c from Comment c
            join fetch c.author
            left join fetch c.media
            where c.post.id = :postId and c.deletedAt is null
              and (c.createdAt > :afterCreatedAt
                   or (c.createdAt = :afterCreatedAt and c.id > :afterId))
            order by c.createdAt asc, c.id asc
            """)
    List<Comment> findThreadAfter(
            @Param("postId") UUID postId,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    @Query("""
            select c from Comment c
            join fetch c.author
            left join fetch c.media
            where c.id = :id and c.deletedAt is null
            """)
    Optional<Comment> findLiveById(@Param("id") UUID id);

    /** Serialises evidence capture with a concurrent author edit or deletion. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from Comment c where c.id = :id")
    Optional<Comment> findForEvidence(@Param("id") UUID id);

    /** Backs the authoring rate limit. Deleted comments still count: the cost being limited was already paid. */
    long countByAuthorIdAndCreatedAtAfter(UUID authorId, Instant since);

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    /** The comment half of {@code PostRepository.findAllIdsByAuthor}, on the same terms. */
    @Query("select c.id from Comment c where c.author.id = :authorId")
    List<UUID> findAllIdsByAuthor(@Param("authorId") UUID authorId);
}
