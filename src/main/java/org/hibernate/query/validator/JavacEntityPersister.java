package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Names;
import org.hibernate.QueryException;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Queryable;
import org.hibernate.type.*;

import javax.lang.model.type.MirroredTypeException;
import javax.persistence.*;

class JavacEntityPersister extends MockEntityPersister {

    private final Symbol type;
    private final Names names;
    private final MockSessionFactory factory;

    JavacEntityPersister(String entityName, Symbol type, Names names,
                         MockSessionFactory factory) {
        super(entityName, factory);
        this.type = type;
        this.names = names;
        this.factory = factory;
    }

    @Override
    public Type getIdentifierType() {
        //TODO: getPropertyType(getIdentifierPropertyName())
        return StandardBasicTypes.INTEGER;
    }

    @Override
    public String getIdentifierPropertyName() {
        //TODO!!!!!!
        return "id";
    }

    @Override
    public Type getPropertyType(String name) throws QueryException {
        String[] segments = name.split("\\.");
        Symbol.TypeSymbol memberType = type.type.tsym;
        String memberEntityName = null;
        for (String segment: segments) {
            Symbol member = null;
            while (member==null && memberType instanceof Symbol.ClassSymbol) {
                member = HQLValidatingProcessor.lookup(names, memberType, segment);
                if (member==null) {
                    com.sun.tools.javac.code.Type superclass =
                            ((Symbol.ClassSymbol) memberType).getSuperclass();
                    if (superclass!=null) {
                        memberType = superclass.tsym;
                    }
                }
            }
            if (member == null || member.getAnnotation(Transient.class)!=null) {
                return null;
            }
            else {
                memberType = member.type.tsym;
                memberEntityName = targetEntityName(member);
            }
        }
        return memberEntityName != null ? entity(memberEntityName) : type(memberType);
    }

    private String targetEntityName(Symbol member) {
        Class targetEntity = null;
        boolean toMany = false;

        ManyToOne mto = member.getAnnotation(ManyToOne.class);
        if (mto!=null) {
            targetEntity = mto.targetEntity();
        }
        try {
            OneToOne oto = member.getAnnotation(OneToOne.class);
            if (oto != null) {
                targetEntity = oto.targetEntity();
            }
            OneToMany otm = member.getAnnotation(OneToMany.class);
            if (otm != null) {
                targetEntity = otm.targetEntity();
                toMany = true;
            }
            ManyToMany mtm = member.getAnnotation(ManyToMany.class);
            if (mtm != null) {
                targetEntity = mtm.targetEntity();
                toMany = true;
            }
        }
        catch (MirroredTypeException mte) {
            targetEntity = void.class;
        }

        if (targetEntity!=null) {
            if (targetEntity.equals(void.class)) {
                return toMany ? null : member.type.tsym.name.toString();
            }
            else {
                return targetEntity.getSimpleName();
            }
        }

        return null;
    }

    private Type entity(String entityName) {
        EntityPersister ep = factory.createMockEntityPersister(entityName);
        if (ep instanceof Queryable) {
            return ((Queryable) ep).getType();
        }
        return null;
    }

    private Type type(Symbol.TypeSymbol symbol) {
        switch (symbol.asType().getTag()) {
            case BOOLEAN:
                return BooleanType.INSTANCE;
            case SHORT:
                return ShortType.INSTANCE;
            case INT:
                return IntegerType.INSTANCE;
            case LONG:
                return LongType.INSTANCE;
            case BYTE:
                return ByteType.INSTANCE;
            case CHAR:
                return CharacterType.INSTANCE;
            case FLOAT:
                return FloatType.INSTANCE;
            case DOUBLE:
                return DoubleType.INSTANCE;
            case CLASS:
                switch (symbol.flatName().toString()) {
                    case "java.lang.String":
                        return StringType.INSTANCE;
                    //TODO: JDK wrapper types!!!
                }
        }
        //TODO: embeddable types!!!!
        //rubbishy default
        return StringType.INSTANCE;
    }

}
