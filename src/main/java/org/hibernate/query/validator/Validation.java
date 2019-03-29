package org.hibernate.query.validator;

import antlr.NoViableAltException;
import antlr.RecognitionException;
import antlr.collections.AST;
import org.hibernate.HibernateException;
import org.hibernate.QueryException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.internal.antlr.HqlTokenTypes;
import org.hibernate.hql.internal.ast.*;
import org.hibernate.hql.internal.ast.util.ASTUtil;
import org.hibernate.hql.internal.ast.util.NodeTraverser;
import org.hibernate.param.NamedParameterSpecification;
import org.hibernate.param.ParameterSpecification;
import org.hibernate.param.PositionalParameterSpecification;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyMap;
import static java.util.regex.Pattern.compile;
import static org.hibernate.internal.util.StringHelper.qualifier;
import static org.hibernate.internal.util.StringHelper.unqualify;

class Validation {

    static void validate(String hql,
                         Set<Integer> setParameterLabels,
                         Set<String> setParameterNames,
                         ParseErrorHandler handler,
                         SessionFactoryImplementor factory) {

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
                    ;
                }


                try {
                    List<ParameterSpecification> specs = walker.getParameterSpecs();
                    String unsetParams = null;
                    String notSet = null;
                    for (ParameterSpecification spec : specs) {
                        if (spec instanceof NamedParameterSpecification) {
                            String name = ((NamedParameterSpecification) spec).getName();
                            if (!setParameterNames.contains(name)) {
                                notSet = unsetParams==null ? " is not set" : " are not set";
                                unsetParams = unsetParams==null ? "" : unsetParams + ", ";
                                unsetParams += ':' + name;
                                //TODO: report the error at the correct offset
                                //int loc = walker.getNamedParameterLocations(name)[0];
                            }
                        }
                        else if (spec instanceof PositionalParameterSpecification) {
                            int label = ((PositionalParameterSpecification) spec).getLabel();
                            if (!setParameterLabels.contains(label)) {
                                notSet = unsetParams==null ? " is not set" : " are not set";
                                unsetParams = unsetParams==null ? "" : unsetParams + ", ";
                                unsetParams += "?" + label;
                                //TODO: report the error at the correct offset
                                //int loc = walker.getNamedParameterLocations(name)[0];
                            }
                        }
                    }
                    if (unsetParams!=null) {
                        handler.reportWarning(unsetParams + notSet);
                    }
                    for (ParameterSpecification spec : specs) {
                        if (spec instanceof NamedParameterSpecification) {
                            String name = ((NamedParameterSpecification) spec).getName();
                            setParameterNames.remove(name);
                        }
                        else if (spec instanceof PositionalParameterSpecification) {
                            int label = ((PositionalParameterSpecification) spec).getLabel();
                            setParameterLabels.remove(label);
                        }
                    }
                    if (!setParameterNames.isEmpty() || !setParameterLabels.isEmpty()) {
                        String missingParams = ':'
                                + setParameterNames.stream()
                                .reduce((names, name) -> names + ", :" + name)
                                .orElse("")
                                + setParameterLabels.stream().map(Object::toString)
                                .reduce((names, label) -> names + ", ?" + label)
                                .orElse("");
                        String notOccur = setParameterNames.size()+setParameterLabels.size() == 1 ?
                                " does not occur in the query" :
                                " do not occur in the query";
                        handler.reportWarning(missingParams + notOccur);
                    }
                }
                finally {
                    setParameterNames.clear();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class JavaConstantConverter implements NodeTraverser.VisitationStrategy {
        private final SessionFactoryImplementor factory;
        private AST dotRoot;

        private JavaConstantConverter(SessionFactoryImplementor factory) {
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
                "[a-z\\d]+\\.([A-Z]{1}[a-z\\d]+)+\\$?([A-Z]{1}[a-z\\d]+)*\\.[A-Z_\\$]+",
                Pattern.UNICODE_CHARACTER_CLASS);

        private boolean isConstantValue(String name, SessionFactoryImplementor factory) {
            return (!factory.getSessionFactoryOptions().isConventionalJavaConstants()
                    || JAVA_CONSTANT_PATTERN.matcher(name).matches())
                && ((MockSessionFactory) factory).isFieldDefined(
                    qualifier(name), unqualify(name));
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

    private static class Filter implements ParseErrorHandler {
        private ParseErrorHandler delegate;
        private int errorCount;

        private Filter(ParseErrorHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getErrorCount() {
            return errorCount;
        }

        @Override
        public void throwQueryException() throws QueryException {
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
