package org.hibernate.query.validator;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.util.Names;
import org.hibernate.QueryException;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeKind;
import java.util.Map;

import static org.hibernate.query.validator.JavacSessionFactory.superclassLookup;
import static org.hibernate.query.validator.MockSessionFactory.typeHelper;
import static org.hibernate.query.validator.MockSessionFactory.typeResolver;

class JavacEntityPersister extends MockEntityPersister {

    private final Symbol type;
    private final Names names;

    JavacEntityPersister(String entityName, Symbol type, Names names,
                         MockSessionFactory factory) {
        super(entityName, factory);
        this.type = type;
        this.names = names;
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
        com.sun.tools.javac.code.Type memberType = type.type;
        String memberEntityName = null;
        //iterate over the path segments
        for (String segment: name.split("\\.")) {
            Symbol member = superclassLookup(names, memberType.tsym, segment);
            if (member == null || isTransient(member)) {
                return null;
            }
            else {
                memberType = member.type;
                memberEntityName = targetEntityName(member);
            }
        }
        return memberEntityName != null ?
                typeHelper.entity(memberEntityName) :
                typeResolver.basic(qualifiedName(memberType));
    }

    private boolean isTransient(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            if (qualifiedName((ClassType) mirror.getAnnotationType())
                    .equals("javax.persistence.Transient")) {
                return true;
            }
        }
        return false;
    }

    private String targetEntityName(Symbol member) {
        for (AnnotationMirror mirror : member.getAnnotationMirrors()) {
            String targetEntity;
            switch (qualifiedName((ClassType) mirror.getAnnotationType())) {
                case "javax.persistence.ManyToOne":
                case "javax.persistence.OneToOne":
                    targetEntity = targetEntity(mirror);
                    return targetEntity==null ?
                            simpleName(member.type) :
                            targetEntity;
                case "javax.persistence.ManyToMany":
                case "javax.persistence.OneToMany":
                    targetEntity = targetEntity(mirror);
                    return targetEntity==null ?
                            simpleName(member.type.getTypeArguments().head) :
                            targetEntity;
            }
        }
        return null;
    }

    private static String simpleName(com.sun.tools.javac.code.Type type) {
        return type==null ? null : type.tsym.name.toString();
    }

    private static String qualifiedName(com.sun.tools.javac.code.Type type) {
        return type==null ? null : type.tsym.flatName().toString();
    }

    private static String targetEntity(AnnotationMirror mirr) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                mirr.getElementValues().entrySet()) {
            if (entry.getKey().getSimpleName().toString().equals("targetEntity")) {
                ClassType classType = (ClassType) entry.getValue().getValue();
                return classType.getKind() == TypeKind.VOID ? null :
                        classType.tsym.name.toString();
            }
        }
        return null;
    }

//    private Type type(TypeSymbol symbol) {
//        switch (symbol.asType().getTag()) {
//            case BOOLEAN:
//                return BooleanType.INSTANCE;
//            case SHORT:
//                return ShortType.INSTANCE;
//            case INT:
//                return IntegerType.INSTANCE;
//            case LONG:
//                return LongType.INSTANCE;
//            case BYTE:
//                return ByteType.INSTANCE;
//            case CHAR:
//                return CharacterType.INSTANCE;
//            case FLOAT:
//                return FloatType.INSTANCE;
//            case DOUBLE:
//                return DoubleType.INSTANCE;
//            case CLASS:
//                switch (symbol.flatName().toString()) {
//                    case "java.lang.String":
//                        return StringType.INSTANCE;
//                    //TODO: JDK wrapper types!!!
//                }
//        }
//        //TODO: embeddable types!!!!
//        //rubbishy default
//        return StringType.INSTANCE;
//    }

}
