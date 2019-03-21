package org.hibernate.query.validator.test;

import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
public class Person {
    public String name;
    @OneToOne
    public Address address;
}
