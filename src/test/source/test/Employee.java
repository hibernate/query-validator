package test;

import java.util.Map;
import javax.persistence.Entity;
import javax.persistence.Access;
import javax.persistence.ManyToMany;

import static javax.persistence.AccessType.PROPERTY;

@Entity @Access(PROPERTY)
public class Employee extends Person {
    private Integer id;
    private Map<String, Person> contacts;

    public Integer getId() { return id; }

    @ManyToMany
    Map<String, Person> getContacts() { return contacts; }
}
