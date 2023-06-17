package test;

import jakarta.persistence.Entity;
import jakarta.persistence.Basic;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.NamedQueries;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Parameters;

@Entity
@NamedQueries({
    @NamedQuery(name="ok", query="from PanachePerson p where p.id=1"),
})
public class PanachePerson extends PanacheEntity {
    public String name;
    public String title;
    @Basic(optional=false)
    public Sex sex;
    public boolean completed;
    
    public static PanachePerson findByName(String name) {
        return find("name", name).firstResult();
    }
    
    public static void goodQueries() {
        find("name", "foo");
        find("name = ?1 and id = ?2", "foo", 2);
        find("name = :name and id = :id", Parameters.with("name", "foo").and("id", 2));
        find("name", Sort.by("name"), "foo");
        find("name = ?1 and id = ?2", Sort.by("name"), "foo", 2);
        find("name = :name and id = :id", Sort.by("name"), Parameters.with("name", "foo").and("id", 2));
        update("name", "foo");
        delete("name", "foo");
        count("name", "foo");

        find("order by name");
        find("from Person");
        String stef = "";
        find("completed = 0 and title like ?1", "%"+stef+"%");
    }
}
