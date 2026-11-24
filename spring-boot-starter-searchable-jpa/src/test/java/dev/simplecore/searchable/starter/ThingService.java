package dev.simplecore.searchable.starter;

import dev.simplecore.searchable.core.service.DefaultSearchableService;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class ThingService extends DefaultSearchableService<Thing, Long> {

    public ThingService(ThingRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
