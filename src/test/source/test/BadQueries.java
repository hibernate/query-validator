package test;

//leave blank line or update test error locations
//leave blank line or update test error locations

public class BadQueries {

    public void badQueries() {
        createQuery("do"); //syntax error
        createQuery("from"); //syntax error
        createQuery("from Person where p.name='gavin' select"); //syntax error
        createQuery("from Person p where p.name+='gavin'"); //syntax error
        createQuery("select from Person where p.name='gavin'"); //syntax error
        if (1 == ".".length()) {
            createQuery("select new test.Nil(p,a) from Person p join p.address a"); //error
            createQuery("select new test.Pair(p) from Person p"); //error
            createQuery("select new test.Pair(p,p.name) from Person p"); //error
        }
        createQuery("from People p where p.name='gavin'"); //error
        createQuery("from Person p where p.firstName='gavin'"); //error
        createQuery("from Person p join p.addr a"); //error
        createQuery("from Person p where p.address.town='barcelona'"); //error
        createQuery("from Person p where p.name in (select a.name from Address a)"); //error
        createQuery("from Address add where add.country.type='au'"); //error
        for (int i=0; i<100; i++) {
            createQuery("select e from Employee e join e.contacts c where key(c).length > 0"); //error
            createQuery("select p.name, n from Person p join p.notes n where n.length>0"); //error
        }
        createQuery("select xxx from Person"); //warning
        createQuery("select func(p.name), year(current_date) from Person p"); //warning
        createQuery("from Person p where p.name = function('custom', p.id)"); //warning
        createQuery("select upper(p.name), year(current_date) from Person"); //error + warning

        createQuery("select p.name, n from Person p join p.notes n where key(n) = 0"); //error
        createQuery("select p.name, n from Person p join p.notes n where value(n) = ''"); //error
        createQuery("select p.name, n from Person p join p.notes n where entry(n) is not null"); //error
        createQuery("select p.name, n from Person p join p.notes n where index(n) = 0"); //error

        createQuery("select e from Employee e join e.contacts c where entry(c).value.address is null"); //error

        createQuery("from Person p where p.name = ? and p.id > ?"); //error

        createQuery("from Person p where max(indices(p.notes)) > 1"); //should be error!
        createQuery("from Person p where sum(elements(p.notes)) = ''"); //should be error!

        createQuery("from Person p where p.name = ?1 and p.id > ?2")
                .setParameter(1, "").getResultList(); //JPQL positional args
        createQuery("from Person p where p.name = :name and p.id >= :minId")
                .setParameter("minId", 12).getResultList(); //JPQL named args

        createQuery("from Person p").setParameter("hello", "world").getResultList();

        createQuery("select new test.Pair(1,1) from Person p"); //"select new" with literals
        createQuery("select new test.Pair('','') from Person p"); //"select new" with literals

        createQuery("from Person p where p.name='gavin"); //error
        createQuery("from Person p where p.firstName='gavin"); //error

        createQuery("from" + "Person"); //error missing space in concatenated query
        createQuery("from " + "Person" + " order by 1");
    }

    private static Query createQuery(String s) { return new Query(); }
    private static class Query {
        public Query setParameter(String s, Object o) { return this; }
        public Query setParameter(int i, Object o) { return this; }
        public Query getResultList() { return this; }
    }
}