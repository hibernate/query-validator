package org.hibernate.query.validator;

import antlr.SemanticException;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import org.hibernate.hql.internal.ast.DetailedSemanticException;
import org.hibernate.hql.internal.ast.tree.ConstructorNode;
import org.hibernate.hql.internal.ast.tree.PathNode;
import org.hibernate.type.Type;

import java.util.List;

import static org.hibernate.query.validator.JavacHelper.lookup;
import static org.hibernate.query.validator.JavacHelper.lookupPackage;

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
            String packageName;
            String className;
            int index = path.lastIndexOf('.');
            if (index>0) {
                packageName = path.substring(0, index);
                className = path.substring(index+1);
            }
            else {
                packageName = "";
                className = path;
            }
            PackageSymbol pack = lookupPackage(packageName);
            Symbol symbol = lookup(pack, className);
            if (!(symbol instanceof ClassSymbol)) {
                throw new DetailedSemanticException("Class " + path + " not found");
            }
            List<Type> argumentTypeList = getConstructorArgumentTypeList();
            for (Symbol cons: symbol.members().getElements(Symbol::isConstructor)) {
                MethodSymbol constructor = (MethodSymbol) cons;
                if (constructor.params.length()==argumentTypeList.size()) {
                    //TODO: check constructor parameter types!
                    //      no good way to do this without
                    //      loading classes, unfortunately
                    return; //matching constructor found!
                }
            }
            throw new DetailedSemanticException("No suitable constructor for class " + path);
        }
    }

}
