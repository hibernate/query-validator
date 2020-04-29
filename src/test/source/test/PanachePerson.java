package test;

import javax.persistence.Id;
import javax.persistence.Entity;
import javax.persistence.Basic;
import javax.persistence.Access;
import javax.persistence.ElementCollection;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.NamedQuery;
import javax.persistence.NamedQueries;
import java.util.Set;
import java.util.Date;
import java.util.List;
import io.quarkus.hibernate.orm.panache.PanacheEntity;

import static javax.persistence.AccessType.PROPERTY;

@Entity
@NamedQueries({
    @NamedQuery(name="ok", query="from PanachePerson p where p.id=1"),
    @NamedQuery(name="broke", query="from PanachePerson p where p.x=1")
})
public class PanachePerson extends PanacheEntity {
    public String name;
    @Basic(optional=false)
    public Sex sex;
    public Date dob;
//    @OneToOne
//    public Address address;
//    @OneToMany(targetEntity = Address.class)
//    public Set<Address> pastAddresses;
//    @ElementCollection
//    public List<String> notes;
//    @Access(PROPERTY)
//    public String getWhatever() { return "thing"; };
//    @ElementCollection
//    public Set<Email> emails;
//    @ManyToOne
//    public Person emergencyContact;
    
    public static PanachePerson findByName(String name) {
        return find("name", name).firstResult();
    }
}
