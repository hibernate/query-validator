package test;

public class Queries {

    public void run() {
        createQuery("from Person p where lower(p.name)='gavin'");
        createQuery("from Employee p where p.name='gavin' and p.id=111");
        createQuery("from Person p join p.address a where a.city='barcelona'");
        createQuery("from Person p where p.address.city='barcelona'");
        createQuery("from Person p join p.addresses a where a.city='barcelona'");

        createQuery("do"); //syntax error
        createQuery("from"); //syntax error
        createQuery("from Person where p.name='gavin' select"); //syntax error
        createQuery("from Person p where p.name+='gavin'"); //syntax error
        createQuery("select from Person where p.name='gavin'"); //syntax error

        createQuery("from People p where p.name='gavin'"); //error
        createQuery("from Person p where p.firstName='gavin'"); //error
        createQuery("from Person p join p.addr a"); //error
        createQuery("from Person p where p.address.town='barcelona'"); //error

        createQuery("from Person p where p.name in (select p.name from Person p)"); //"in" operator with subquery
        createQuery("from Person p where exists (select a from p.addresses a)"); //"exists" operator with correlated subquery
        createQuery("from Person p where p.name in (select a.name from Address a)");

        createQuery("select new test.Pair(p,a) from Person p join p.address a");
        createQuery("select new test.Pair(p,p.address) from Person p join p.address a");
        createQuery("select new test.Nil(p,a) from Person p join p.address a");
        createQuery("select new test.Pair(p) from Person p");
        createQuery("select new test.Pair(p,p.name) from Person p");

        createQuery("from Person p where size(p.addresses) = 0"); //JPQL "size()" function
        createQuery("from Person p where exists elements(p.addresses)"); //HQL "exists" operator with "elements()" function

        createQuery("from Person p where year(p.dob) > 1974"); //JPQL "year()" function
        createQuery("select cast(p.dob as string) from Person p"); //JPQL "cast()" function

        createQuery("select e, c, key(c) from Employee e join e.contacts c where key(c) = 'boss'"); //JPQL "key()" operator for Map
        createQuery("select e, entry(c) from Employee e join e.contacts c where c.address is null"); //JPQL "entry()" operator for Map
        createQuery("select e, value(c) from Employee e join e.contacts c where value(c).address is null"); //JPQL "value()" operator for Map
        createQuery("select distinct key(contact) from Employee e join e.contacts contact where length(key(contact))<10 and contact.address is not null and contact.address.city is not null");

        createQuery("select substring(note,0,50) from Person p join p.notes note where index(note)<10"); //JPQL "index()" function for List
        createQuery("select substring(note,0,50) from Person p join p.notes note where length(note)>3"); //JPQL "length()" function for String
        createQuery("from Person p where size(p.notes) > 1"); //JPQL "size()" operator for collections

        createQuery("from Person p where '' = minelement(p.notes)"); //HQL "minelement()" function
        createQuery("from Person p where '' = maxelement(p.notes)"); //HQL "maxelement()" function
        createQuery("from Person p where maxindex(p.notes) > 1"); //HQL "maxindex()" function
        createQuery("from Person p where minindex(p.notes) > 1"); //HQL "minindex()" function
        createQuery("from Person p where 1 in indices(p.notes)"); //"in" operator with HQL "indices()" function
        createQuery("from Person p where '' in elements(p.notes)"); //"in" operator with HQL "elements()" function
        createQuery("from Person p where p.notes is not empty"); //JPQL "is not empty" operator
        createQuery("from Person p where '' member of p.notes"); //JPQL "member of" operator

        createQuery("select e from Employee e join e.contacts c where entry(c).value.address is null");

        createQuery("select xxx from Person");

        createQuery("from Person p where type(p) = Employee");  //JPQL "type()" function

        createQuery("from Person p where all(select a.city from p.addresses a) = 'barcelona'"); //"all" operator with correlated subquery
        createQuery("from Person p where any(select a.city from p.addresses a) = 'barcelona'"); //"any" operator with correlated subquery
        createQuery("from Person p where '' = all elements(p.notes)"); //"all" operator with correlated HQL "elements()" function
        createQuery("from Person p where 1 < any indices(p.notes)"); //"any" operator with correlated HQL "indices()" function
        createQuery("from Employee e where 'boss' = some indices(e.contacts)"); //"some" operator with correlated HQL "indices()" function
//        createQuery("from Person p where max(indices(p.notes)) > 1"); //error!
//        createQuery("from Person p where sum(elements(p.notes)) = ''"); //error!

        createQuery("select p.name, n from Person p join p.notes n where length(n)>0"); //join an element collection
        createQuery("select p.name, n from Person p join p.notes n where n.length>0"); //error
        createQuery("from Person p where p.notes[0] is not null"); //HQL list indexing operator
        createQuery("from Employee e where e.contacts['boss'] is not null"); //HQL list indexing operator

        createQuery("from Address add where add.country.code='au'");
        createQuery("from Address add where add.country.type='au'");

        createQuery("select p.whatever from Person p where p.whatever is not null");
        createQuery("select p.address.country.thing from Person p where p.address.country.thing is null");
    }

    private static void createQuery(String s) {}
}