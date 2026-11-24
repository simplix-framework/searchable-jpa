package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

// docs:start service
@Service
public class PostService extends DefaultSearchableService<Post, Long> {

    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
// docs:end service
