package test;

import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.Basic;
import jakarta.persistence.Access;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.NamedQueries;
import java.util.Set;
import java.util.Date;
import java.util.List;

import static jakarta.persistence.AccessType.PROPERTY;

@Entity
@NamedQueries({
    @NamedQuery(name="ok", query="from Person p where p.id=1"),
    @NamedQuery(name="broke", query="from Person p where p.x=1")
})
public class Person {
    @Id long id;
    public String name;
    @Basic(optional=false)
    public Sex sex;
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
    @ManyToOne
    public Person emergencyContact;
}
