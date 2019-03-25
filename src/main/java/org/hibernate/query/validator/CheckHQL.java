package org.hibernate.query.validator;

public @interface CheckHQL {
    public boolean strict() default true;
}