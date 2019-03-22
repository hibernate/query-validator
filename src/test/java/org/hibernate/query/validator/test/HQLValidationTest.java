package org.hibernate.query.validator.test;

import org.hibernate.query.validator.CheckHQL;

@CheckHQL
public class HQLValidationTest {
//    @CheckHQL
    public void run() {
        createQuery("from Person p where p.name='gavin'");
        createQuery("from Employee p where p.name='gavin' and p.id=111");
        createQuery("from Person p join p.address a where a.city='barcelona'");
        createQuery("from Person p where p.address.city='barcelona'");

//        createQuery("do");
//        createQuery("from");
//        createQuery("from Person where p.name='gavin' select");
//        createQuery("from Person p where p.name+='gavin'");
//        createQuery("select from Person where p.name='gavin'");
//
//        createQuery("from People p where p.name='gavin'");
//        createQuery("from Person p where p.firstName='gavin'");
//        createQuery("from Person p join p.addr a");
//        createQuery("from Person p where p.address.town='barcelona'");
    }

    private static void createQuery(String s) {}
}