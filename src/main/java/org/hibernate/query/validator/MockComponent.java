package org.hibernate.query.validator;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.CompositeUserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class MockComponent implements CompositeUserType {

    @Override
    public Object getPropertyValue(Object component, int property)
            throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPropertyValue(Object component, int property, Object value)
            throws HibernateException {
        throw new UnsupportedOperationException();
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
    public Object nullSafeGet(ResultSet rs, String[] names,
                              SharedSessionContractImplementor session, Object owner)
            throws HibernateException, SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,
                            SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
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
    public Serializable disassemble(Object value,
                                    SharedSessionContractImplementor session)
            throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object assemble(Serializable cached,
                           SharedSessionContractImplementor session, Object owner)
            throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object replace(Object original, Object target,
                          SharedSessionContractImplementor session, Object owner)
            throws HibernateException {
        throw new UnsupportedOperationException();
    }
}
