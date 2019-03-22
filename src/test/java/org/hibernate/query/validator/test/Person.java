package org.hibernate.query.validator.test;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import java.util.Set;

@Entity
public class Person {
    public String name;
    @OneToOne
    public Address address;
    @OneToMany(targetEntity = Address.class)
    public Set<Address> addresses;
}
