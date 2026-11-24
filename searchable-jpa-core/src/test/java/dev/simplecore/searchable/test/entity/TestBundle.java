package dev.simplecore.searchable.test.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * An entity with a collection of plain values, used to search through an {@code @ElementCollection}
 * and to compare text with and without regard to case.
 */
@Entity
@Getter
@Setter
@Table(name = "test_bundle")
public class TestBundle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bundleId;

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "test_bundle_label", joinColumns = @JoinColumn(name = "bundle_id"))
    @Column(name = "label", nullable = false)
    private Set<String> labels = new LinkedHashSet<>();
}
