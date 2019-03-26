package org.hibernate.query.validator;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class MockUserType implements UserType {
    @Override
    public int[] sqlTypes() {
        return new int[0];
    }

    @Override
    public Class returnedClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        throw new UnsupportedOperationException();
    }
}
