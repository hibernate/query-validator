package org.hibernate.query.validator;

import antlr.*;
import antlr.collections.AST;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.antlr.HqlBaseParser;
import org.hibernate.hql.internal.ast.util.ASTUtil;

import java.io.StringReader;

class HqlParser extends HqlBaseParser {
//    private Map<String, Set<String>> treatMap;
//    private ParseErrorHandler parseErrorHandler;

    HqlParser(String hql) {
        super(new HqlLexer(new StringReader(hql)));
        this.setASTFactory(new HqlASTFactory());
    }

//    public void reportError(RecognitionException e) {
//        parseErrorHandler.reportError(e);
//    }
//
//    public void reportError(String s) {
//        parseErrorHandler.reportError(s);
//    }
//
//    public void reportWarning(String s) {
//        parseErrorHandler.reportWarning(s);
//    }

//    public Map<String, Set<String>> getTreatMap() {
//        return treatMap;
//    }

    public AST handleIdentifierError(Token token, RecognitionException ex)
            throws RecognitionException, TokenStreamException {
        if (token instanceof HqlToken) {
            HqlToken hqlToken = (HqlToken)token;
            if (hqlToken.isPossibleID()
                    && ex instanceof MismatchedTokenException) {
                MismatchedTokenException mte = (MismatchedTokenException)ex;
                if (mte.expecting == 108) {
                    this.reportWarning("Keyword  '" + token.getText() + "' is being interpreted as an identifier due to: " + mte.getMessage());
                    ASTPair currentAST = new ASTPair();
                    token.setType(96);
                    this.astFactory.addASTChild(currentAST, this.astFactory.create(token));
                    this.consume();
                    return currentAST.root;
                }
            }
        }

        return super.handleIdentifierError(token, ex);
    }

    public AST negateNode(AST x) {
        switch(x.getType()) {
        case 6:
            x.setType(40);
            x.setText("{or}");
            x.setFirstChild(this.negateNode(x.getFirstChild()));
            x.getFirstChild().setNextSibling(this.negateNode(x.getFirstChild().getNextSibling()));
            return x;
        case 10:
            x.setType(85);
            x.setText("{not}" + x.getText());
            return x;
        case 26:
            x.setType(86);
            x.setText("{not}" + x.getText());
            return x;
        case 34:
            x.setType(87);
            x.setText("{not}" + x.getText());
            return x;
        case 40:
            x.setType(6);
            x.setText("{and}");
            x.setFirstChild(this.negateNode(x.getFirstChild()));
            x.getFirstChild().setNextSibling(this.negateNode(x.getFirstChild().getNextSibling()));
            return x;
        case 82:
            x.setType(83);
            x.setText("{not}" + x.getText());
            return x;
        case 83:
            x.setType(82);
            x.setText("{not}" + x.getText());
            return x;
        case 85:
            x.setType(10);
            x.setText("{not}" + x.getText());
            return x;
        case 86:
            x.setType(26);
            x.setText("{not}" + x.getText());
            return x;
        case 87:
            x.setType(34);
            x.setText("{not}" + x.getText());
            return x;
        case 105:
            x.setType(112);
            x.setText("{not}" + x.getText());
            return x;
        case 112:
            x.setType(105);
            x.setText("{not}" + x.getText());
            return x;
        case 114:
            x.setType(117);
            x.setText("{not}" + x.getText());
            return x;
        case 115:
            x.setType(116);
            x.setText("{not}" + x.getText());
            return x;
        case 116:
            x.setType(115);
            x.setText("{not}" + x.getText());
            return x;
        case 117:
            x.setType(114);
            x.setText("{not}" + x.getText());
            return x;
        default:
            AST not = super.negateNode(x);
            if (not != x) {
                not.setNextSibling(x.getNextSibling());
                x.setNextSibling((AST)null);
            }

            return not;
        }
    }

    public AST processEqualityExpression(AST x) {
        if (x == null) {
//            LOG.processEqualityExpression();
            return null;
        } else {
            int type = x.getType();
            if (type != 105 && type != 112) {
                return x;
            } else {
                boolean negated = type == 112;
                if (x.getNumberOfChildren() == 2) {
                    AST a = x.getFirstChild();
                    AST b = a.getNextSibling();
                    if (a.getType() == 39 && b.getType() != 39) {
                        return this.createIsNullParent(b, negated);
                    } else if (b.getType() == 39 && a.getType() != 39) {
                        return this.createIsNullParent(a, negated);
                    } else {
                        return b.getType() == 64 ? this.processIsEmpty(a, negated) : x;
                    }
                } else {
                    return x;
                }
            }
        }
    }

    private AST createIsNullParent(AST node, boolean negated) {
        node.setNextSibling((AST)null);
        int type = negated ? 82 : 83;
        String text = negated ? "is not null" : "is null";
        return ASTUtil.createParent(this.astFactory, type, text, node);
    }

    private AST processIsEmpty(AST node, boolean negated) {
        node.setNextSibling((AST)null);
        AST ast = this.createSubquery(node);
        ast = ASTUtil.createParent(this.astFactory, 19, "exists", ast);
        if (!negated) {
            ast = ASTUtil.createParent(this.astFactory, 38, "not", ast);
        }

        return ast;
    }

    private AST createSubquery(AST node) {
        AST ast = ASTUtil.createParent(this.astFactory, 90, "RANGE", node);
        ast = ASTUtil.createParent(this.astFactory, 22, "from", ast);
        ast = ASTUtil.createParent(this.astFactory, 92, "SELECT_FROM", ast);
        ast = ASTUtil.createParent(this.astFactory, 89, "QUERY", ast);
        return ast;
    }

//    public void showAst(AST ast, PrintStream out) {
//        this.showAst(ast, new PrintWriter(out));
//    }

//    private void showAst(AST ast, PrintWriter pw) {
//        TokenPrinters.HQL_TOKEN_PRINTER.showAst(ast, pw);
//    }

    public void matchOptionalFrom() throws RecognitionException, TokenStreamException {
        this.returnAST = null;
        ASTPair currentAST = new ASTPair();
        AST optionalFrom_AST = null;
        if (this.LA(1) == 22 && this.LA(2) != 15) {
            this.match(22);
            optionalFrom_AST = currentAST.root;
            this.returnAST = optionalFrom_AST;
        }

    }

    public void firstPathTokenWeakKeywords() throws TokenStreamException {
        int t = this.LA(1);
        switch(t) {
        case 15:
            this.LT(0).setType(108);
        default:
        }
    }

    public void handlePrimaryExpressionDotIdent() throws TokenStreamException {
        if (this.LA(2) == 15 && this.LA(3) != 108) {
            HqlToken t = (HqlToken)this.LT(3);
            if (t.isPossibleID()) {
                t.setType(108);
//                if (LOG.isDebugEnabled()) {
//                    LOG.debugf("handleDotIdent() : new LT(3) token - %s", this.LT(1));
//                }
            }
        }

    }

    public void weakKeywords() throws TokenStreamException {
        int t = this.LA(1);
        switch(t) {
        case 24:
        case 41:
            if (this.LA(2) != 109) {
                this.LT(1).setType(108);
//                if (LOG.isDebugEnabled()) {
//                    LOG.debugf("weakKeywords() : new LT(1) token - %s", this.LT(1));
//                }
            }
            break;
        default:
            if (this.LA(0) == 22 && t != 108 && this.LA(2) == 15) {
                HqlToken hqlToken = (HqlToken)this.LT(1);
                if (hqlToken.isPossibleID()) {
                    hqlToken.setType(108);
//                    if (LOG.isDebugEnabled()) {
//                        LOG.debugf("weakKeywords() : new LT(1) token - %s", this.LT(1));
//                    }
                }
            }
        }

    }

    public void expectNamedParameterName() throws TokenStreamException {
        if (this.LA(1) != 108) {
            HqlToken nextToken = (HqlToken)this.LT(1);
            if (nextToken.isPossibleID()) {
//                LOG.debugf("Converting keyword [%s] following COLON to IDENT as an expected parameter name", nextToken.getText());
                nextToken.setType(108);
            }
        }

    }

    public void handleDotIdent() throws TokenStreamException {
        if (this.LA(1) == 15 && this.LA(2) != 108) {
            HqlToken t = (HqlToken)this.LT(2);
            if (t.isPossibleID()) {
                this.LT(2).setType(108);
//                if (LOG.isDebugEnabled()) {
//                    LOG.debugf("handleDotIdent() : new LT(2) token - %s", this.LT(1));
//                }
            }
        }

    }

    public void processMemberOf(Token n, AST p, ASTPair currentAST) {
        AST inNode = n == null ? this.astFactory.create(26, "in") : this.astFactory.create(86, "not in");
        this.astFactory.makeASTRoot(currentAST, inNode);
        AST inListNode = this.astFactory.create(80, "inList");
        inNode.addChild(inListNode);
        AST elementsNode = this.astFactory.create(17, "elements");
        inListNode.addChild(elementsNode);
        elementsNode.addChild(p);
    }

    protected void registerTreat(AST pathToTreat, AST treatAs) {
//        String path = this.toPathText(pathToTreat);
//        String subclassName = this.toPathText(treatAs);
////        LOG.debugf("Registering discovered request to treat(%s as %s)", path, subclassName);
//        if (this.treatMap == null) {
//            this.treatMap = new HashMap();
//        }
//
//        Set<String> subclassNames = (Set)this.treatMap.get(path);
//        if (subclassNames == null) {
//            subclassNames = new HashSet();
//            this.treatMap.put(path, subclassNames);
//        }
//
//        ((Set)subclassNames).add(subclassName);
    }

    private String toPathText(AST node) {
        String text = node.getText();
        return text.equals(".") && node.getFirstChild() != null && node.getFirstChild().getNextSibling() != null && node.getFirstChild().getNextSibling().getNextSibling() == null ? this.toPathText(node.getFirstChild()) + '.' + this.toPathText(node.getFirstChild().getNextSibling()) : text;
    }
//
//    public Map<String, Set<String>> getTreatMap() {
//        return this.treatMap == null ? Collections.emptyMap() : this.treatMap;
//    }

    public static void panic() {
        throw new QueryException("Parser: panic");
    }
}
