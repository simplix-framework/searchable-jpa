package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Searches the server composes itself, as the documentation shows them. */
@Component
public class PostQueries {

    private final PostService service;

    public PostQueries(PostService service) {
        this.service = service;
    }

    // docs:start builder
    public Page<Post> publishedAboutSpringOrJpa() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.PUBLISHED))
                .and(g -> g.contains("title", "Spring").orContains("title", "JPA"))
                .sort(s -> s.desc("createdAt").asc("id"))
                .page(0).size(20)
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end builder

    // docs:start builder-operators
    public Page<Post> popularOrFeatured() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.between("viewCount", 100L, 500L).orIn("label", List.of("featured")))
                .and(g -> g.isNotNull("publishedAt"))
                .sort(s -> s.asc("id"))
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end builder-operators

    // docs:start builder-values
    public Page<Post> viewedPublishedOrArchived() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.between("viewCount", 100L, 500L)
                        .in("status", List.of(PostStatus.PUBLISHED, PostStatus.ARCHIVED)))
                .sort(s -> s.asc("id"))
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end builder-values

    // docs:start builder-or
    public Page<Post> draftsOrPopular() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.DRAFT))
                .or(g -> g.greaterThanOrEqualTo("viewCount", 400L).equals("status", PostStatus.PUBLISHED))
                .sort(s -> s.asc("id"))
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end builder-or

    // docs:start builder-fetch
    public Page<Post> newestWithAuthor() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .sort(s -> s.desc("createdAt"))
                .fetchFields("author")
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end builder-fetch

    // docs:start extend
    public Page<Post> publishedOnly(SearchCondition<PostSearchDTO> fromClient) {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.from(fromClient, PostSearchDTO.class)
                .and(g -> g.equals("status", PostStatus.PUBLISHED))
                .build();
        return service.findAllWithSearch(condition);
    }
    // docs:end extend

    // docs:start single
    public Optional<Post> newestPublished() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.PUBLISHED))
                .sort(s -> s.desc("createdAt"))
                .build();
        return service.findFirstWithSearch(condition);
    }

    public Optional<Post> theOnlyDraft() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.DRAFT))
                .build();
        return service.findOneWithSearch(condition);
    }
    // docs:end single

    // docs:start count
    public long countPublished(SearchCondition<PostSearchDTO> condition) {
        return service.countWithSearch(condition);
    }

    public boolean anyMatch(SearchCondition<PostSearchDTO> condition) {
        return service.existsWithSearch(condition);
    }
    // docs:end count

    // docs:start interface-projection
    public Page<PostSummary> summaries(SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition, PostSummary.class);
    }
    // docs:end interface-projection

    // docs:start fetch
    public Page<Post> withAuthorAndComments(SearchCondition<PostSearchDTO> condition) {
        condition.setFetchFields(Set.of("author", "comments"));
        return service.findAllWithSearch(condition);
    }
    // docs:end fetch

    // docs:start scope
    public Page<Post> ofTenant(SearchCondition<PostSearchDTO> fromClient, Long tenantId) {
        Specification<Post> ownTenant = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        return service.findAllWithSearch(fromClient, ownTenant);
    }
    // docs:end scope
}
