package org.hibernate.query.validator;

import antlr.NoViableAltException;
import antlr.RecognitionException;
import antlr.Token;
import antlr.TokenStream;
import antlr.collections.AST;
import org.hibernate.HibernateException;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.antlr.HqlBaseLexer;
import org.hibernate.hql.internal.antlr.HqlTokenTypes;
import org.hibernate.hql.internal.ast.*;
import org.hibernate.hql.internal.ast.util.ASTUtil;
import org.hibernate.hql.internal.ast.util.NodeTraverser;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Stream.concat;
import static org.hibernate.internal.util.StringHelper.qualifier;
import static org.hibernate.internal.util.StringHelper.unqualify;

class Validation {

    interface Handler extends ParseErrorHandler {
        void error(int start, int end, String message);
        void warn(int start, int end, String message);
    }

    static void validate(String hql, boolean checkParams,
                         Set<Integer> setParameterLabels,
                         Set<String> setParameterNames,
                         Handler handler,
                         MockSessionFactory factory) {

        handler = new Filter(handler);

        try {

            HqlParser parser = HqlParser.getInstance(hql);
            setHandler(parser, handler);
            parser.statement();

            if (handler.getErrorCount() == 0) {
                new NodeTraverser(new JavaConstantConverter(factory))
                        .traverseDepthFirst(parser.getAST());

                HqlSqlWalker walker = new HqlSqlWalker(
                        new QueryTranslatorImpl("", hql, emptyMap(), factory),
                        factory, parser, emptyMap(), null);
                walker.setASTFactory(new SqlASTFactory(walker) {
                    @Override
                    public Class<?> getASTNodeType(int tokenType) {
                        return tokenType == CONSTRUCTOR ?
                                WorkaroundConstructorNode.class :
                                super.getASTNodeType(tokenType);
                    }
                });
                setHandler(walker, handler);
                try {
                    walker.statement(parser.getAST());
                } catch (HibernateException e) {
                    String message = e.getMessage();
                    if (message != null) {
                        handler.reportError(message);
                    }
                } catch (Exception e) {
                    //throw away NullPointerExceptions and the like
                    //since I guess they represent bugs in Hibernate
//                    e.printStackTrace();
                }

                if (checkParams) {
                    try {
                        String unsetParams = null;
                        String notSet = null;
                        int start = -1;
                        int end = -1;
                        List<String> names = new ArrayList<>();
                        List<Integer> labels = new ArrayList<>();
                        TokenStream tokens = new HqlBaseLexer(new StringReader(hql));
                        loop:
                        while (true) {
                            Token token = tokens.nextToken();
                            switch (token.getType()) {
                                case HqlTokenTypes.EOF:
                                    break loop;
                                case HqlTokenTypes.PARAM:
                                case HqlTokenTypes.COLON:
                                    Token next = tokens.nextToken();
                                    String text = next.getText();
                                    switch (token.getType()) {
                                        case HqlTokenTypes.COLON:
                                            if (next.getType() == HqlTokenTypes.IDENT) {
                                                names.add(text);
                                                if (setParameterNames.contains(text)) {
                                                    continue;
                                                }
                                            }
                                            break;
                                        case HqlTokenTypes.PARAM:
                                            if (next.getType() == HqlTokenTypes.NUM_INT) {
                                                int label;
                                                try {
                                                    label = parseInt(text);
                                                } catch (NumberFormatException nfe) {
                                                    continue;
                                                }
                                                labels.add(label);
                                                if (setParameterLabels.contains(label)) {
                                                    continue;
                                                }
                                            }
                                            break;
                                        default:
                                            continue;
                                    }
                                    notSet = unsetParams == null ? " is not set" : " are not set";
                                    unsetParams = unsetParams == null ? "" : unsetParams + ", ";
                                    unsetParams += token.getText() + text;
                                    if (start == -1)
                                        start = token.getColumn(); //TODO: wrong for multiline query strings!
                                    end = token.getColumn() + text.length();
                                    break;
                            }
                        }
                        if (unsetParams != null) {
                            handler.warn(start, end, unsetParams + notSet);
                        }

                        setParameterNames.removeAll(names);
                        setParameterLabels.removeAll(labels);

                        int count = setParameterNames.size() + setParameterLabels.size();
                        if (count > 0) {
                            String missingParams =
                                    concat(setParameterNames.stream().map(name -> ":" + name),
                                            setParameterLabels.stream().map(label -> "?" + label))
                                            .reduce((x, y) -> x + ", " + y)
                                            .orElse(null);
                            String notOccur =
                                    count == 1 ?
                                            " does not occur in the query" :
                                            " do not occur in the query";
                            handler.reportWarning(missingParams + notOccur);
                        }
                    } finally {
                        setParameterNames.clear();
                        setParameterLabels.clear();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class JavaConstantConverter implements NodeTraverser.VisitationStrategy {
        private final MockSessionFactory factory;
        private AST dotRoot;

        private JavaConstantConverter(MockSessionFactory factory) {
            this.factory = factory;
        }

        @Override
        public void visit(AST node) {
            if (dotRoot != null) {
                // we are already processing a dot-structure
                if (ASTUtil.isSubtreeChild(dotRoot, node)) {
                    return;
                }
                // we are now at a new tree level
                dotRoot = null;
            }

            if (node.getType() == HqlTokenTypes.DOT) {
                dotRoot = node;
                handleDotStructure(dotRoot);
            }
        }

        private void handleDotStructure(AST dotStructureRoot) {
            final String expression = ASTUtil.getPathText(dotStructureRoot);
            if (isConstantValue(expression, factory)) {
                dotStructureRoot.setFirstChild(null);
                dotStructureRoot.setType(HqlTokenTypes.JAVA_CONSTANT);
                dotStructureRoot.setText(expression);
            }
        }

        private static final Pattern JAVA_CONSTANT_PATTERN = Pattern.compile(
                "([a-z\\d]+\\.)+([A-Z][a-z\\d]+)+\\$?([A-Z][a-z\\d]+)*\\.[A-Z_$]+",
                Pattern.UNICODE_CHARACTER_CLASS);

        private boolean isConstantValue(String name, MockSessionFactory factory) {
            return (!factory.getSessionFactoryOptions().isConventionalJavaConstants()
                    || JAVA_CONSTANT_PATTERN.matcher(name).matches())
                && factory.isFieldDefined(qualifier(name), unqualify(name));
        }
    }


    private static void setHandler(Object object, ParseErrorHandler handler) {
        try {
            Field field = object.getClass().getDeclaredField("parseErrorHandler");
            field.setAccessible(true);
            field.set(object, handler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class Filter implements Handler {
        private Handler delegate;
        private int errorCount;

        private Filter(Handler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getErrorCount() {
            return errorCount;
        }

        @Override
        public void throwQueryException() throws QueryException {}

        @Override
        public void error(int start, int end, String message) {
            delegate.error(start, end, message);
        }

        @Override
        public void warn(int start, int end, String message) {
            delegate.warn(start, end, message);
        }

        @Override
        public void reportError(RecognitionException e) {
            if (errorCount > 0 && e instanceof NoViableAltException) {
                //ignore it, it's probably a useless "unexpected end of subtree"
                return;
            }

            String text = e.getMessage();
            switch (text) {
                case "node did not reference a map":
                    text = "key(), value(), or entry() argument must be map element";
                    break;
                case "entry(*) expression cannot be further de-referenced":
                    text = "entry() has no members";
                    break;
                case "FROM expected (non-filter queries must contain a FROM clause)":
                    text = "missing from clause or select list";
                    break;
            }

            errorCount++;
            delegate.reportError(new RecognitionException(text,
                    e.fileName, e.line, e.column));
        }

        @Override
        public void reportError(String text) {
            Matcher matcher =
                    compile("Unable to resolve path \\[(.*)\\], unexpected token \\[(.*)\\]")
                            .matcher(text);
            if (matcher.matches()
                    && matcher.group(1)
                    .startsWith(matcher.group(2))) {
                text = matcher.group(1) + " is not defined";
            }

            if (text.startsWith("Legacy-style query parameters")) {
                text = "illegal token: ? (use ?1, ?2)";
            }

            errorCount++;
            delegate.reportError(text);
        }

        @Override
        public void reportWarning(String text) {
            delegate.reportWarning(text);
        }
    }
}
