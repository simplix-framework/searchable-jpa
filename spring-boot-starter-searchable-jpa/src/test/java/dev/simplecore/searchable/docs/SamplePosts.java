package dev.simplecore.searchable.docs;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** The rows every example in the documentation is run against. */
final class SamplePosts {

    private SamplePosts() {
    }

    static void load(AuthorRepository authors, TagRepository tags, PostRepository posts) {
        Author kim = authors.save(new Author("Minjun Kim", "Kim"));
        Author lee = authors.save(new Author("Seoyeon Lee", "Lee"));
        Author park = authors.save(new Author("Jiho Park", "Park"));
        Tag spring = tags.save(new Tag("spring"));
        Tag guide = tags.save(new Tag("guide"));
        Tag jpa = tags.save(new Tag("jpa"));
        Tag draft = tags.save(new Tag("draft"));

        Post basics = new Post("Spring Boot basics", PostStatus.PUBLISHED, 120L,
                Instant.parse("2026-10-01T09:00:00Z"), 1L, kim);
        basics.setPublishedAt(LocalDateTime.parse("2026-10-01T09:30:00"));
        basics.getComments().add(new Comment(basics, "Good intro", "lee"));
        basics.getComments().add(new Comment(basics, "Too short", "kim"));
        basics.getTags().addAll(List.of(spring, guide));
        basics.getLabels().add("featured");

        Post paging = new Post("JPA paging in depth", PostStatus.PUBLISHED, 480L,
                Instant.parse("2026-10-03T09:00:00Z"), 1L, lee);
        paging.setPublishedAt(LocalDateTime.parse("2026-10-03T10:00:00"));
        paging.getComments().add(new Comment(paging, "Good examples", "kim"));
        paging.getComments().add(new Comment(paging, "Needs update", "lee"));
        paging.getTags().add(jpa);
        paging.getLabels().addAll(List.of("featured", "long-read"));

        Post tuning = new Post("Query tuning draft", PostStatus.DRAFT, 15L,
                Instant.parse("2026-10-05T09:00:00Z"), 1L, kim);
        tuning.getTags().addAll(List.of(draft, jpa));

        Post security = new Post("Spring Security notes", PostStatus.ARCHIVED, 300L,
                Instant.parse("2026-10-07T09:00:00Z"), 2L, park);
        security.setPublishedAt(LocalDateTime.parse("2026-10-07T12:00:00"));
        security.getComments().add(new Comment(security, "Good", "lee"));
        security.getTags().add(spring);

        posts.saveAll(List.of(basics, paging, tuning, security));
    }
}
