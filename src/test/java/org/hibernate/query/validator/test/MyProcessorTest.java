package org.hibernate.query.validator.test;

import org.hibernate.query.validator.CheckHQL;

@CheckHQL
public class MyProcessorTest {
//    @CheckHQL
    public void run() {
        System.out.print("hello");
        createQuery("from People p where p.name='gavin'");
    }

    private static void createQuery(String s) {}
}