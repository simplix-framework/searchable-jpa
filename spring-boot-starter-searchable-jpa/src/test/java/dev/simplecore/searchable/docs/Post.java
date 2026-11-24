package dev.simplecore.searchable.docs;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// docs:start columns
@Entity
public class Post {

    @Id
    @GeneratedValue
    private Long id;
    private String title;
    @Enumerated(EnumType.STRING)
    private PostStatus status;
    private Long viewCount;
    private Instant createdAt;
    private LocalDateTime publishedAt;
    private Long tenantId;
    // docs:end columns

    // docs:start associations
    @ManyToOne(fetch = FetchType.LAZY)
    private Author author;
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    private List<Comment> comments = new ArrayList<>();
    @ManyToMany
    private Set<Tag> tags = new LinkedHashSet<>();
    @ElementCollection
    private Set<String> labels = new LinkedHashSet<>();
    // docs:end associations

    protected Post() {
    }

    public Post(String title, PostStatus status, Long viewCount, Instant createdAt, Long tenantId, Author author) {
        this.title = title;
        this.status = status;
        this.viewCount = viewCount;
        this.createdAt = createdAt;
        this.tenantId = tenantId;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Author getAuthor() {
        return author;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Set<String> getLabels() {
        return labels;
    }
// docs:start columns
}
// docs:end columns
