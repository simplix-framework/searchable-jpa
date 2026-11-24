package dev.simplecore.searchable.docs;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

// docs:start entity
@Entity
public class Author {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String familyName;
    // docs:end entity

    protected Author() {
    }

    public Author(String name, String familyName) {
        this.name = name;
        this.familyName = familyName;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFamilyName() {
        return familyName;
    }
// docs:start entity
}
// docs:end entity
