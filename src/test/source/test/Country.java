package test;

import javax.persistence.Embeddable;

@Embeddable
public class Country {
    public String code;
    public String name;
    public int thing = 0;
}
