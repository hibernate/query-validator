package org.hibernate.query.validator;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.QueryException;
import org.hibernate.grammars.hql.HqlLexer;
import org.hibernate.grammars.hql.HqlParser;
import org.hibernate.query.hql.internal.HqlParseTreeBuilder;
import org.hibernate.query.hql.internal.SemanticQueryBuilder;
import org.hibernate.query.sqm.EntityTypeException;
import org.hibernate.query.sqm.PathElementException;
import org.hibernate.query.sqm.TerminalPathException;
import org.hibernate.type.descriptor.java.spi.JdbcTypeRecommendationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.Character.isJavaIdentifierStart;
import static java.lang.Integer.parseInt;
import static java.util.stream.Stream.concat;

/**
 * @author Gavin King
 */
class Validation {

    interface Handler extends ANTLRErrorListener {
        void error(int start, int end, String message);
        void warn(int start, int end, String message);

        int getErrorCount();
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
//        handler = new Filter(handler, errorOffset);

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
            if (handler.getErrorCount() == 0) {
                try {
                    new SemanticQueryBuilder<>( Object[].class, () -> false, factory )
                            .visitStatement( statementContext );
                }
                catch (JdbcTypeRecommendationException ignored) {
                    // just squash these for now
                }
                catch (QueryException | PathElementException | TerminalPathException | EntityTypeException
                       | PropertyNotFoundException se) { //TODO is this one really thrown by core? It should not be!
                    handler.error( -errorOffset+1, -errorOffset + hql.length(), se.getMessage() );
                }
            }

            if (checkParams) {
                checkParameterBinding(hql, setParameterLabels, setParameterNames, handler, errorOffset);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkParameterBinding(
            String hql,
            Set<Integer> setParameterLabels,
            Set<String> setParameterNames,
            Handler handler,
            int errorOffset) {
        try {
            String unsetParams = null;
            String notSet = null;
            String parameters = null;
            int start = -1;
            int end = -1;
            List<String> names = new ArrayList<>();
            List<Integer> labels = new ArrayList<>();
            final HqlLexer hqlLexer = HqlParseTreeBuilder.INSTANCE.buildHqlLexer( hql );
            loop:
            while (true) {
                Token token = hqlLexer.nextToken();
                int tokenType = token.getType();
                switch (tokenType) {
                    case HqlLexer.EOF:
                        break loop;
                    case HqlLexer.QUESTION_MARK:
                    case HqlLexer.COLON:
                        Token next = hqlLexer.nextToken();
                        String text = next.getText();
                        switch (tokenType) {
                            case HqlLexer.COLON:
                                if (!text.isEmpty()
                                        && isJavaIdentifierStart(text.codePointAt(0))) {
                                    names.add(text);
                                    if (setParameterNames.contains(text)) {
                                        continue;
                                    }
                                }
                                else {
                                    continue;
                                }
                                break;
                            case HqlLexer.QUESTION_MARK:
                                if (next.getType() == HqlLexer.INTEGER_LITERAL) {
                                    int label;
                                    try {
                                        label = parseInt(text);
                                    }
                                    catch (NumberFormatException nfe) {
                                        continue;
                                    }
                                    labels.add(label);
                                    if (setParameterLabels.contains(label)) {
                                        continue;
                                    }
                                }
                                else {
                                    continue;
                                }
                                break;
                            default:
                                continue;
                        }
                        parameters = unsetParams == null ? "Parameter " : "Parameters ";
                        notSet = unsetParams == null ? " is not set" : " are not set";
                        unsetParams = unsetParams == null ? "" : unsetParams + ", ";
                        unsetParams += token.getText() + text;
                        if (start == -1)
                            start = token.getCharPositionInLine(); //TODO: wrong for multiline query strings!
                        end = token.getCharPositionInLine() + text.length() + 1;
                        break;
                }
            }
            if (unsetParams != null) {
                handler.warn(start-errorOffset+1, end-errorOffset, parameters + unsetParams + notSet);
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
                String params =
                        count == 1 ?
                                "Parameter " :
                                "Parameters ";
                String notOccur =
                        count == 1 ?
                                " does not occur in the query" :
                                " do not occur in the query";
                handler.warn(0, 0, params + missingParams + notOccur);
            }
        }
        finally {
            setParameterNames.clear();
            setParameterLabels.clear();
        }
    }


//    private static class Filter implements Handler {
//        private final Handler delegate;
//        private final int errorOffset;
//        private int errorCount;
//
//        @Override
//        public int getErrorCount() {
//            return errorCount;
//        }
//
//        private Filter(Handler delegate, int errorOffset) {
//            this.delegate = delegate;
//            this.errorOffset = errorOffset;
//        }
//
//        @Override
//        public void error(int start, int end, String message) {
//            delegate.error(start - errorOffset, end - errorOffset, message);
//        }
//
//        @Override
//        public void warn(int start, int end, String message) {
//            delegate.warn(start - errorOffset, end - errorOffset, message);
//        }
//
//        @Override
//        public void syntaxError(Recognizer<?, ?> recognizer,
//                                Object offendingSymbol,
//                                int line, int charPositionInLine,
//                                String msg,
//                                RecognitionException e) {
////            if (errorCount > 0 && e instanceof NoViableAltException) {
////                //ignore it, it's probably a useless "unexpected end of subtree"
////                return;
////            }
//            errorCount++;
//            delegate.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
//        }
//
//        @Override
//        public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
//        }
//
//        @Override
//        public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
//        }
//
//        @Override
//        public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
//        }
//    }
}
