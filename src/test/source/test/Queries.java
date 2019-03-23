package test;

public class Queries {

    public void run() {
        createQuery("from Person p where p.name='gavin'");
        createQuery("from Employee p where p.name='gavin' and p.id=111");
        createQuery("from Person p join p.address a where a.city='barcelona'");
        createQuery("from Person p where p.address.city='barcelona'");
        createQuery("from Person p join p.addresses a where a.city='barcelona'");

        createQuery("do");
        createQuery("from");
        createQuery("from Person where p.name='gavin' select");
        createQuery("from Person p where p.name+='gavin'");
        createQuery("select from Person where p.name='gavin'");

        createQuery("from People p where p.name='gavin'");
        createQuery("from Person p where p.firstName='gavin'");
        createQuery("from Person p join p.addr a");
        createQuery("from Person p where p.address.town='barcelona'");

        createQuery("from Person p where p.name in (select p.name from Person p)");
        createQuery("from Person p where p.name in (select a.name from Address a)");

        createQuery("select new test.Pair(p,a) from Person p join p.address a");
        createQuery("select new test.Pair(p,p.address) from Person p join p.address a");
        createQuery("select new test.Nil(p,a) from Person p join p.address a");
        createQuery("select new test.Pair(p) from Person p");
        createQuery("select new test.Pair(p,p.name) from Person p");
    }

    private static void createQuery(String s) {}
}