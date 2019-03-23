package test;

import java.util.Map;
import javax.persistence.Entity;
import javax.persistence.ManyToMany;

@Entity
public class Employee extends Person {
    public Integer id;
    @ManyToMany
    public Map<String, Person> contacts;
}
