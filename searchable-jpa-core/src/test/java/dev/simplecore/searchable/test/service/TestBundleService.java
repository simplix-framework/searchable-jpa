package dev.simplecore.searchable.test.service;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.entity.TestBundle;
import dev.simplecore.searchable.test.repository.TestBundleRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class TestBundleService extends DefaultSearchableService<TestBundle, Long> {

    public TestBundleService(TestBundleRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
