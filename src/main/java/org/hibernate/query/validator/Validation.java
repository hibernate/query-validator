package org.hibernate.query.validator;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.hibernate.grammars.hql.HqlLexer;
import org.hibernate.grammars.hql.HqlParser;
import org.hibernate.query.SemanticException;
import org.hibernate.query.hql.internal.HqlParseTreeBuilder;
import org.hibernate.query.sqm.ParsingException;

import java.util.BitSet;
import java.util.Set;

/**
 * @author Gavin King
 */
class Validation {

    interface Handler extends ANTLRErrorListener {
        void error(int start, int end, String message);
        void warn(int start, int end, String message);
    }

    static void validate(String hql, boolean checkParams,
                         Set<Integer> setParameterLabels,
                         Set<String> setParameterNames,
                         Handler handler,
                         MockSessionFactory factory) {
        validate(hql, checkParams, setParameterLabels, setParameterNames, handler, factory, 0);
    }

    static void validate(String hql, boolean checkParams,
                         Set<Integer> setParameterLabels,
                         Set<String> setParameterNames,
                         Handler handler,
                         MockSessionFactory factory,
                         int errorOffset) {
        handler = new Filter(handler, errorOffset);

        try {

            final HqlLexer hqlLexer = HqlParseTreeBuilder.INSTANCE.buildHqlLexer( hql );
            final HqlParser hqlParser = HqlParseTreeBuilder.INSTANCE.buildHqlParser( hql, hqlLexer );
            hqlLexer.addErrorListener( handler );
            hqlParser.getInterpreter().setPredictionMode( PredictionMode.SLL );
            hqlParser.removeErrorListeners();
            hqlParser.addErrorListener( handler );
            hqlParser.setErrorHandler( new BailErrorStrategy() );

            HqlParser.StatementContext statementContext;
            try {
                statementContext = hqlParser.statement();
            }
            catch ( ParseCancellationException e) {
                // reset the input token stream and parser state
                hqlLexer.reset();
                hqlParser.reset();

                // fall back to LL(k)-based parsing
                hqlParser.getInterpreter().setPredictionMode( PredictionMode.LL );
                hqlParser.setErrorHandler( new DefaultErrorStrategy() );

                statementContext = hqlParser.statement();
            }
            catch ( ParsingException ex ) {
                throw new SemanticException( "Illegal HQL syntax [" + ex.getMessage() + "]", hql, ex );
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
//
//            if (handler.getErrorCount() == 0) {
//                new NodeTraverser(new JavaConstantConverter(factory))
//                        .traverseDepthFirst(parser.getAST());
//
//                HqlSqlWalker walker = new HqlSqlWalker(
//                        new QueryTranslatorImpl("", hql, emptyMap(), factory),
//                        factory, parser, emptyMap(), null) {
//                    @Override
//                    public Dialect getDialect() {
//                        return factory.getDialect();
//                    }
//                };
//                walker.setASTFactory(new SqlASTFactory(walker) {
//                    @Override
//                    public Class<?> getASTNodeType(int tokenType) {
//                        return tokenType == CONSTRUCTOR ?
//                                WorkaroundConstructorNode.class :
//                                super.getASTNodeType(tokenType);
//                    }
//                });
//                setHandler(walker, handler);
//                try {
//                    walker.statement(parser.getAST());
//                }
//                catch (HibernateException e) {
//                    String message = e.getMessage();
//                    if (message != null) {
//                        handler.reportError(message);
//                    }
//                }
//                catch (Exception e) {
//                    //throw away NullPointerExceptions and the like
//                    //since I guess they represent bugs in Hibernate
////                    e.printStackTrace();
//                }
//
//                if (checkParams) {
//                    try {
//                        String unsetParams = null;
//                        String notSet = null;
//                        int start = -1;
//                        int end = -1;
//                        List<String> names = new ArrayList<>();
//                        List<Integer> labels = new ArrayList<>();
//                        TokenStream tokens = new HqlBaseLexer(new StringReader(hql));
//                        loop:
//                        while (true) {
//                            Token token = tokens.nextToken();
//                            switch (token.getType()) {
//                                case HqlTokenTypes.EOF:
//                                    break loop;
//                                case HqlTokenTypes.PARAM:
//                                case HqlTokenTypes.COLON:
//                                    Token next = tokens.nextToken();
//                                    String text = next.getText();
//                                    switch (token.getType()) {
//                                        case HqlTokenTypes.COLON:
//                                            if (next.getType() == HqlTokenTypes.IDENT) {
//                                                names.add(text);
//                                                if (setParameterNames.contains(text)) {
//                                                    continue;
//                                                }
//                                            }
//                                            break;
//                                        case HqlTokenTypes.PARAM:
//                                            if (next.getType() == HqlTokenTypes.NUM_INT) {
//                                                int label;
//                                                try {
//                                                    label = parseInt(text);
//                                                }
//                                                catch (NumberFormatException nfe) {
//                                                    continue;
//                                                }
//                                                labels.add(label);
//                                                if (setParameterLabels.contains(label)) {
//                                                    continue;
//                                                }
//                                            }
//                                            break;
//                                        default:
//                                            continue;
//                                    }
//                                    notSet = unsetParams == null ? " is not set" : " are not set";
//                                    unsetParams = unsetParams == null ? "" : unsetParams + ", ";
//                                    unsetParams += token.getText() + text;
//                                    if (start == -1)
//                                        start = token.getColumn(); //TODO: wrong for multiline query strings!
//                                    end = token.getColumn() + text.length();
//                                    break;
//                            }
//                        }
//                        if (unsetParams != null) {
//                            handler.warn(start, end, unsetParams + notSet);
//                        }
//
//                        setParameterNames.removeAll(names);
//                        setParameterLabels.removeAll(labels);
//
//                        int count = setParameterNames.size() + setParameterLabels.size();
//                        if (count > 0) {
//                            String missingParams =
//                                    concat(setParameterNames.stream().map(name -> ":" + name),
//                                            setParameterLabels.stream().map(label -> "?" + label))
//                                            .reduce((x, y) -> x + ", " + y)
//                                            .orElse(null);
//                            String notOccur =
//                                    count == 1 ?
//                                            " does not occur in the query" :
//                                            " do not occur in the query";
//                            handler.reportWarning(missingParams + notOccur);
//                        }
//                    } finally {
//                        setParameterNames.clear();
//                        setParameterLabels.clear();
//                    }
//                }
//            }
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static class JavaConstantConverter implements NodeTraverser.VisitationStrategy {
//        private final MockSessionFactory factory;
//        private AST dotRoot;
//
//        private JavaConstantConverter(MockSessionFactory factory) {
//            this.factory = factory;
//        }
//
//        @Override
//        public void visit(AST node) {
//            if (dotRoot != null) {
//                // we are already processing a dot-structure
//                if (ASTUtil.isSubtreeChild(dotRoot, node)) {
//                    return;
//                }
//                // we are now at a new tree level
//                dotRoot = null;
//            }
//
//            if (node.getType() == HqlTokenTypes.DOT) {
//                dotRoot = node;
//                handleDotStructure(dotRoot);
//            }
//        }
//
//        private void handleDotStructure(AST dotStructureRoot) {
//            final String expression = ASTUtil.getPathText(dotStructureRoot);
//            if (isConstantValue(expression, factory)) {
//                dotStructureRoot.setFirstChild(null);
//                dotStructureRoot.setType(HqlTokenTypes.JAVA_CONSTANT);
//                dotStructureRoot.setText(expression);
//            }
//        }
//
//        private static final Pattern JAVA_CONSTANT_PATTERN = Pattern.compile(
//                "([a-z\\d]+\\.)+([A-Z][a-z\\d]+)+\\$?([A-Z][a-z\\d]+)*\\.[A-Z_$]+",
//                Pattern.UNICODE_CHARACTER_CLASS);
//
//        private boolean isConstantValue(String name, MockSessionFactory factory) {
//            return (!factory.getSessionFactoryOptions().isConventionalJavaConstants()
//                    || JAVA_CONSTANT_PATTERN.matcher(name).matches())
//                && factory.isFieldDefined(qualifier(name), unqualify(name));
//        }
//    }
//
//
//    private static void setHandler(HqlParser object, ParseErrorHandler handler) {
//        try {
//            Field field = HqlParser.class.getDeclaredField("parseErrorHandler");
//            field.setAccessible(true);
//            field.set(object, handler);
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void setHandler(HqlSqlWalker object, ParseErrorHandler handler) {
//        try {
//            Field field = HqlSqlWalker.class.getDeclaredField("parseErrorHandler");
//            field.setAccessible(true);
//            field.set(object, handler);
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
    private static class Filter implements Handler {
        private final Handler delegate;
        private final int errorOffset;
        private int errorCount;

        private Filter(Handler delegate, int errorOffset) {
            this.delegate = delegate;
            this.errorOffset = errorOffset;
        }

        @Override
        public void error(int start, int end, String message) {
            delegate.error(start - errorOffset, end - errorOffset, message);
        }

        @Override
        public void warn(int start, int end, String message) {
            delegate.warn(start - errorOffset, end - errorOffset, message);
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            if (errorCount > 0 && e instanceof NoViableAltException) {
                //ignore it, it's probably a useless "unexpected end of subtree"
                return;
            }

            final String text;
            switch (msg) {
                case "node did not reference a map":
                    text = "key(), value(), or entry() argument must be map element";
                    break;
                case "entry(*) expression cannot be further de-referenced":
                    text = "entry() has no members";
                    break;
                case "FROM expected (non-filter queries must contain a FROM clause)":
                    text = "missing from clause or select list";
                    break;
                default:
                    text = msg;
            }

            errorCount++;
            delegate.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, text, e);
        }

        @Override
        public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
        }

        @Override
        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
        }

        @Override
        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
        }
    }
}
