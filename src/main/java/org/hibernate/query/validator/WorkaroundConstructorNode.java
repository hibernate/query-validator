package org.hibernate.query.validator;

import antlr.SemanticException;
import org.hibernate.hql.internal.ast.DetailedSemanticException;
import org.hibernate.hql.internal.ast.tree.ConstructorNode;
import org.hibernate.hql.internal.ast.tree.PathNode;
import org.hibernate.type.Type;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class WorkaroundConstructorNode extends ConstructorNode {
    @Override
    public void prepare() throws SemanticException {
        try {
            super.prepare();
        }
        catch (DetailedSemanticException dse) {
            //Ugh, ConstructorNode throws an exception when
            //it tries to load the class and can't!
            String path = ((PathNode) getFirstChild()).getPath();
            MockSessionFactory factory = (MockSessionFactory)
                    getSessionFactoryHelper().getFactory();
            if (!factory.isClassDefined(path)) {
                throw new DetailedSemanticException(path + " does not exist");
            }
            @SuppressWarnings("unchecked")
            List<Type> argumentTypes = getConstructorArgumentTypeList();
            if (!factory.isConstructorDefined(path, argumentTypes)) {
                List<String> typeNames = argumentTypes.stream()
                        .map(Type::getName)
                        .collect(toList());
                throw new DetailedSemanticException(path
                        + " has no suitable constructor for types ("
                        + String.join(", ", typeNames) + ")");
            }
        }
    }
}
