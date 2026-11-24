package dev.simplecore.searchable.starter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ThingRepository extends JpaRepository<Thing, Long>, JpaSpecificationExecutor<Thing> {
}
