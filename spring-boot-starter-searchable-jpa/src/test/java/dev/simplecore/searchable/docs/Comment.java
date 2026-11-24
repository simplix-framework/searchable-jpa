package dev.simplecore.searchable.docs;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// docs:start entity
@Entity
@Table(name = "post_comment")
public class Comment {

    @Id
    @GeneratedValue
    private Long id;
    private String content;
    private String writer;
    @ManyToOne(fetch = FetchType.LAZY)
    private Post post;
    // docs:end entity

    protected Comment() {
    }

    public Comment(Post post, String content, String writer) {
        this.post = post;
        this.content = content;
        this.writer = writer;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public String getWriter() {
        return writer;
    }

    public Post getPost() {
        return post;
    }
// docs:start entity
}
// docs:end entity
