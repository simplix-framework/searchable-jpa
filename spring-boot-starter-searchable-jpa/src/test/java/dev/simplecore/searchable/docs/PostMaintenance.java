package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/** Search-based writes, as the documentation shows them. */
@Component
public class PostMaintenance {

    private final PostArchiveService service;

    public PostMaintenance(PostArchiveService service) {
        this.service = service;
    }

    // docs:start update
    public long archiveDrafts(Long tenantId) {
        SearchCondition<PostSearchDTO> drafts = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.DRAFT))
                .build();
        Specification<Post> ownTenant = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);

        StatusPatch patch = new StatusPatch();
        patch.setStatus(PostStatus.ARCHIVED);
        return service.updateWithSearch(drafts, ownTenant, patch);
    }
    // docs:end update

    // docs:start delete
    public long deleteArchived(Long tenantId) {
        SearchCondition<PostSearchDTO> archived = SearchConditionBuilder.create(PostSearchDTO.class)
                .where(w -> w.equals("status", PostStatus.ARCHIVED))
                .build();
        Specification<Post> ownTenant = (root, query, cb) -> cb.equal(root.get("tenantId"), tenantId);
        return service.deleteWithSearch(archived, ownTenant);
    }
    // docs:end delete
}
