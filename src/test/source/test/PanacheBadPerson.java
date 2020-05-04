package test;

import javax.persistence.Entity;
import javax.persistence.Basic;
import javax.persistence.NamedQuery;
import javax.persistence.NamedQueries;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Parameters;

@Entity
@NamedQueries({
    @NamedQuery(name="ok", query="from PanacheBadPerson p where p.id=1"),
    @NamedQuery(name="broke", query="from PanacheBadPerson p where p.x=1")
})
public class PanacheBadPerson extends PanacheEntity {
    public String name;
    @Basic(optional=false)
    public Sex sex;
    
    public static void badQueries() {
        find("missing", "stef"); // property does not exist
        find("name = ?1 and id = ?2", "stef"); // missing positional arg
        find("name = :name and id = :id", Parameters.with("name", "stef")); // missing named arg
        find("name"); // missing required param for name
        find("name = :name and id = :id and id = :bar", Parameters.with("name", "stef").and("id", "foo")); // missing named arg
        find("name", Sort.descending("name")); // missing positional arg
        list("name"); // missing positional arg
        stream("name"); // missing positional arg
        delete("name"); // missing positional arg
        update("name"); // missing positional arg
        count("name"); // missing positional arg
        exists("name"); // missing positional arg
        find("name", 123, 345); // too many params for name
        find("", Sort.by("missing")); // property does not exist
        find("", Sort.by("name").and("missing")); // property does not exist
    }
}
