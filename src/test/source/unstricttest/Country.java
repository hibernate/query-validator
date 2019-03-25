package unstricttest;

import javax.persistence.Embeddable;
import javax.persistence.OneToMany;
import java.util.Set;

@Embeddable
public class Country {
    public String code;
    public String name;
    public int thing = 0;
    @OneToMany
    public Set<Person> residents;
}
