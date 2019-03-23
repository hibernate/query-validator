package test;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.util.Set;
import java.util.Date;

@Entity
public class Person {
    public String name;
    @OneToOne
    public Address address;
    @OneToMany(targetEntity = Address.class)
    public Set<Address> addresses;
    public Date dob;
}
