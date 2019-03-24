package test;

import javax.persistence.Id;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
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
