package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.service.SearchableServiceDelegate;
import dev.simplecore.searchable.core.service.SearchableServiceSupport;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

// docs:start composition
@Service
public class PostArchiveService implements SearchableServiceSupport<Post, Long> {

    private final SearchableServiceDelegate<Post, Long> delegate;

    public PostArchiveService(PostRepository repository, EntityManager entityManager,
                              PlatformTransactionManager transactionManager) {
        this.delegate = new SearchableServiceDelegate<>(repository, entityManager, Post.class, transactionManager);
    }

    @Override
    public SearchableServiceDelegate<Post, Long> getSearchableDelegate() {
        return delegate;
    }
}
// docs:end composition
