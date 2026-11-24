package dev.simplecore.searchable.docs;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

// docs:start entity
@Entity
public class Tag {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    // docs:end entity

    protected Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
// docs:start entity
}
// docs:end entity
