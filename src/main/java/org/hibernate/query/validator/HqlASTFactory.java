package org.hibernate.query.validator;

import antlr.ASTFactory;
import org.hibernate.hql.internal.ast.tree.Node;

class HqlASTFactory extends ASTFactory {
    HqlASTFactory() {
    }

    public Class getASTNodeType(int tokenType) {
        return Node.class;
    }
}
