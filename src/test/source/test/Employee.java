package test;

import java.util.Map;
import jakarta.persistence.Entity;
import jakarta.persistence.Access;
import jakarta.persistence.ManyToMany;

import static jakarta.persistence.AccessType.PROPERTY;

@Entity @Access(PROPERTY)
public class Employee extends Person {
    private Integer id;
    private Map<String, Person> contacts;

    public Integer getEmployeeId() { return id; }

    @ManyToMany
    Map<String, Person> getContacts() { return contacts; }
}
