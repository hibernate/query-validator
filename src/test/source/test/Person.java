package test;

import javax.persistence.Id;
import javax.persistence.Entity;
import javax.persistence.Access;
import javax.persistence.ElementCollection;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.util.Set;
import java.util.Date;
import java.util.List;

import static javax.persistence.AccessType.PROPERTY;

@Entity
public class Person {
    @Id long id;
    public String name;
    public Date dob;
    @OneToOne
    public Address address;
    @OneToMany(targetEntity = Address.class)
    public Set<Address> pastAddresses;
    @ElementCollection
    public List<String> notes;
    @Access(PROPERTY)
    public String getWhatever() { return "thing"; };
    @ElementCollection
    public Set<Email> emails;
}
