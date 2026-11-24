package dev.simplecore.searchable.docs;

import java.time.Instant;

// docs:start response
public record PostResponse(Long id, String title, PostStatus status, Long viewCount, Instant createdAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(post.getId(), post.getTitle(), post.getStatus(), post.getViewCount(),
                post.getCreatedAt());
    }
}
// docs:end response
