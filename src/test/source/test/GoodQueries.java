package test;

public class GoodQueries {

    public void goodQueries() {
        createQuery("from Person p where lower(p.name)='gavin'");
        createQuery("from Employee p where p.name='gavin' and p.employeeId=111");
        createQuery("from Person p join p.address a where a.city='barcelona'");
        createQuery("from Person p where p.address.city='barcelona'");
        createQuery("from Person p join p.pastAddresses a where a.city='barcelona'");

        createQuery("from Person p where p.name in (select p.name from Person p)"); //"in" operator with subquery
        createQuery("from Person p where exists (select a from p.pastAddresses a)"); //"exists" operator with correlated subquery

        createQuery("select new test.Pair(p,a) from Person p join p.address a"); //"select new"
        createQuery("select new test.Pair(p,p.address) from Person p join p.address a"); //"select new"

        createQuery("from Person p where size(p.pastAddresses) = 0"); //JPQL "size()" function
        createQuery("from Person p where exists elements(p.pastAddresses)"); //HQL "exists" operator with "elements()" function

        createQuery("from Person p where year(p.dob) > 1974"); //HQL "year()" function
        createQuery("select cast(p.dob as string) from Person p"); //HQL "cast()" function

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
        createQuery("from Person p where p.notes is empty"); //JPQL "is empty" operator
        createQuery("from Person p where p.notes is not empty"); //JPQL "is not empty" operator
        createQuery("select e from Employee e join e.contacts c where value(c).address is null");

        createQuery("from Person p where type(p) = Employee");  //JPQL "type()" function

        createQuery("from Person p where all(select a.city from p.pastAddresses a) = 'barcelona'"); //"all" operator with correlated subquery
        createQuery("from Person p where any(select a.city from p.pastAddresses a) = 'barcelona'"); //"any" operator with correlated subquery
        createQuery("from Person p where '' = all elements(p.notes)"); //"all" operator with correlated HQL "elements()" function
        createQuery("from Person p where 1 < any indices(p.notes)"); //"any" operator with correlated HQL "indices()" function
        createQuery("from Employee e where 'boss' = some indices(e.contacts)"); //"some" operator with correlated HQL "indices()" function

        createQuery("select p.name, n from Person p join p.notes n where length(n)>0"); //join an element collection
        createQuery("from Person p where p.notes[0] is not null"); //HQL list indexing operator
        createQuery("from Employee e where e.contacts['boss'] is not null"); //HQL map indexing operator
        createQuery("from Employee e where e.contacts['boss'].id = 222"); //HQL map indexing operator

        createQuery("from Address add where add.country.code='au'");

        createQuery("select p.whatever from Person p where p.whatever is not null");
        createQuery("select p.address.country.thing from Person p where p.address.country.thing is null");

        createQuery("select p2 from Person p join p.address.country.residents p2"); //join association in embeddable
        createQuery("from Person p where p.notes is empty"); //JPQL "is empty" operator for element collection
        createQuery("from Person p where p.pastAddresses is empty"); //JPQL "is empty" operator for association
        createQuery("from Address a where a.country.residents is empty"); //JPQL "is empty" operator for association in embeddable
        createQuery("from Person p where p.address.country.residents is empty"); //JPQL "is empty" operator
        createQuery("from Person p where p.address.currentResidents is empty"); //JPQL "is empty" operator

        createQuery("select a, c from Address a join a.country c"); //fake join to a component
        createQuery("select p, c from Person p join p.address.country c"); //fake join to a component
        createQuery("select c.code, c.name from Address a join a.country c"); //fake join to a component

        createQuery("select e from Person p join p.emails e");
        createQuery("select e.address from Person p join p.emails e");
        createQuery("select e from Person p join p.emails e where e.address is not null and length(e.address)>0");




        createQuery("from Person p where treat(p.emergencyContact as Employee).employeeId = 2"); //JPQL "treat as" operator
        createQuery("from Person p join treat(p.emergencyContact as Employee) c where c.employeeId = 2"); //JPQL "treat as" operator
        createQuery("from Employee e join treat(e.contacts as Employee) c where c.employeeId = 2"); //JPQL "treat as" operator

        createQuery("from Employee e where e.emergencyContact member of e.contacts"); //JPQL "member of" operator with association
        createQuery("from Employee e where e.emergencyContact not member of e.contacts"); //JPQL "not member of" operator with association
        createQuery("from Person p where '' member of p.notes"); //JPQL "member of" operator with element collection
        createQuery("from Person p where '' not member of p.notes"); //JPQL "not member of" operator with element collection

        createQuery("select upper(p.name), year(current_date) from Person p");
        createQuery("select upper(p.name), year(current_date) from Person p where year(current_date) = 2019 and upper(p.name) = 'GAVIN'");

        createQuery("select nullif(e.name, ''), coalesce(e.employeeId, e.id, e.name) from Employee e"); //JPQL "nullif()" and "coalesce()"
        createQuery("select str(p.dob) from Person p"); //HQL "str()" function
        createQuery("select cast(p.dob as string) from Person p"); //SQL "cast()" function
        createQuery("select extract(month from p.dob), extract(year from p.dob) from Person p"); //SQL "extract()" function
        createQuery("select function('bit_length', e.name) from Employee e"); //JPQL "function()" passthrough

        createQuery("from Person p where p.sex = test.Sex.FEMALE");

        createQuery("from Person p where p.name = ?1 and p.id > ?2"); //JPQL positional args
        createQuery("from Person p where p.name = :name and p.id >= :minId"); //JPQL named args
    }

    @SuppressWarnings("hql.unknown-function")
    public void okQueries() {
        createQuery("select xxx from Person"); //warning
        createQuery("select func(p.name), year(current_date) from Person p"); //warning
        createQuery("from Person p where p.name = function('custom', p.id)"); //warning
    }

    private static void createQuery(String s) {}
}