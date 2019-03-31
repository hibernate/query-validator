package org.hibernate.query.validator;

import antlr.NoViableAltException;
import antlr.RecognitionException;
import antlr.collections.AST;
import org.hibernate.HibernateException;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.antlr.HqlTokenTypes;
import org.hibernate.hql.internal.ast.*;
import org.hibernate.hql.internal.ast.util.ASTUtil;
import org.hibernate.hql.internal.ast.util.NodeTraverser;

import java.lang.reflect.Field;
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

    private static final Pattern NAMED_PARAMETERS = Pattern.compile(":(\\w+)\\b");
    private static final Pattern LABELED_PARAMETERS = Pattern.compile("\\?(\\d+)\\b");

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
                    public Class getASTNodeType(int tokenType) {
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

                if (checkParams) try {
                    //TODO: handle ? and : occurring in quoted strings!
                    if (hql.indexOf(':')>0 || hql.indexOf('?')>0) {
                        String unsetParams = null;
                        String notSet = null;
                        int start = -1;
                        int end = -1;
                        Matcher names = NAMED_PARAMETERS.matcher(hql);
                        while (names.find()) {
                            String name = names.group(1);
                            if (!setParameterNames.contains(name)) {
                                notSet = unsetParams == null ? " is not set" : " are not set";
                                unsetParams = unsetParams == null ? "" : unsetParams + ", ";
                                unsetParams += ':' + name;
                                if (start==-1) start = names.start(1);
                                end = names.end(1);
                            }
                        }
                        Matcher labels = LABELED_PARAMETERS.matcher(hql);
                        while (labels.find()) {
                            int label = parseInt(labels.group(1));
                            if (!setParameterLabels.contains(label)) {
                                notSet = unsetParams == null ? " is not set" : " are not set";
                                unsetParams = unsetParams == null ? "" : unsetParams + ", ";
                                unsetParams += "?" + label;
                                if (start==-1) start = labels.start(1);
                                end = labels.end(1);
                            }
                        }
                        if (unsetParams != null) {
                            handler.warn(start, end, unsetParams + notSet);
                        }

                        names.reset();
                        while (names.find()) {
                            String name = names.group(1);
                            setParameterNames.remove(name);
                        }
                        labels.reset();
                        while (labels.find()) {
                            int label = parseInt(labels.group(1));
                            setParameterLabels.remove(label);
                        }
                    }

                    int count = setParameterNames.size() + setParameterLabels.size();
                    if (count>0) {
                        String missingParams =
                            concat(setParameterNames.stream().map(name->':'+name),
                                    setParameterLabels.stream().map(label -> "?" + label))
                                    .reduce((x, y) -> x + ", " + y)
                                    .orElse(null);
                        String notOccur =
                                count == 1 ?
                                " does not occur in the query" :
                                " do not occur in the query";
                        handler.reportWarning(missingParams + notOccur);
                    }
                }
                finally {
                    setParameterNames.clear();
                    setParameterLabels.clear();
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
                "([a-z\\d]+\\.)+([A-Z]{1}[a-z\\d]+)+\\$?([A-Z]{1}[a-z\\d]+)*\\.[A-Z_\\$]+",
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
