package test;

import javax.persistence.Entity;

@Entity
public class Address {
    public String street;
    public String city;
    public String zip;
    public Country country;
}
