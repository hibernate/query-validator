package test;

import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.util.Set;

@Entity
public class Address {
    @Id long id;
    public String street;
    public String city;
    public String zip;
    public Country country;
    @OneToMany(mappedBy = "address")
    public Set<Person> currentResidents;
}
