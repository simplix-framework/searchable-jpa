package dev.simplecore.searchable.test.repository;

import dev.simplecore.searchable.test.entity.TestBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TestBundleRepository extends JpaRepository<TestBundle, Long>, JpaSpecificationExecutor<TestBundle> {
}
