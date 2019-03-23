package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.Type;
import org.hibernate.usertype.CompositeUserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.query.validator.JavacHelper.isPersistent;
import static org.hibernate.query.validator.JavacSessionFactory.propertyType;

class JavacComponent implements CompositeUserType {
    private String[] propertyNames;
    private Type[] propertyTypes;

    JavacComponent(com.sun.tools.javac.code.Type type, String entityName, String path) {
        List<String> names = new ArrayList<>();
        List<Type> types = new ArrayList<>();
        for (Symbol symbol: type.tsym.members().getElements()) {
            if (isPersistent(symbol)) {
                String name = symbol.name.toString();
                Type propertyType = propertyType(type, entityName, name, path);
                if (propertyType != null) {
                    names.add(name);
                    types.add(propertyType);
                }
            }
        }
        propertyNames = names.toArray(new String[0]);
        propertyTypes = types.toArray(new Type[0]);
    }

    @Override
    public String[] getPropertyNames() {
        return propertyNames;
    }

    @Override
    public Type[] getPropertyTypes() {
        return propertyTypes;
    }

    @Override
    public Object getPropertyValue(Object component, int property) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPropertyValue(Object component, int property, Object value) throws HibernateException {
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
    public Serializable disassemble(Object value, SharedSessionContractImplementor session) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        throw new UnsupportedOperationException();
    }
}