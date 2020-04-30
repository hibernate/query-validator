package test;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import io.quarkus.panache.common.Parameters;

public class PanachePersonRepository implements PanacheRepository<PanachePerson> {
    
    public PanachePerson findByName(String name) {
        return find("name", name).firstResult();
    }
    
    public void goodQueries() {
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
    }
}
