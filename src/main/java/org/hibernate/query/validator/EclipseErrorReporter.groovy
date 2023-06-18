package org.hibernate.query.validator

import org.antlr.v4.runtime.LexerNoViableAltException
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.atn.ATNConfigSet
import org.antlr.v4.runtime.dfa.DFA
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities
import org.hibernate.query.hql.internal.StandardHqlTranslator

/**
 * @author Gavin King
 */
class EclipseErrorReporter implements Validation.Handler {

    private def node
    private def unit
    private def compiler
    private int errorcount
    private final String hql

    EclipseErrorReporter(node, unit, compiler, String hql) {
        this.hql = hql
        this.compiler = compiler
        this.node = node
        this.unit = unit
    }

    @Override
    int getErrorCount() {
        return errorcount
    }

    @Override
    void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line, int charInLine, String message, RecognitionException e) {
        message = StandardHqlTranslator.prettifyAntlrError(symbol, line, charInLine, message, e, hql, false)
        errorcount++
        def result = unit.compilationResult()
        char[] fileName = result.fileName
        int[] lineEnds = result.getLineSeparatorPositions()
        int startIndex
        int stopIndex
        int lineNum = getLineNumber(node.sourceStart, lineEnds, 0, lineEnds.length - 1)
        Token offendingToken = e.getOffendingToken()
        if ( offendingToken != null ) {
            startIndex = offendingToken.getStartIndex()
            stopIndex = offendingToken.getStopIndex()
        }
        else if ( e instanceof LexerNoViableAltException ) {
            startIndex = ((LexerNoViableAltException) e).getStartIndex()
            stopIndex = startIndex;
        }
        else {
            startIndex = lineEnds[line-1] + charInLine
            stopIndex = startIndex
        }
        int startPosition = node.sourceStart + startIndex + 1
        int endPosition = node.sourceStart + stopIndex + 1
        if ( endPosition < startPosition ) {
            endPosition = startPosition
        }
        def problem =
                compiler.problemReporter.problemFactory
                        .createProblem(fileName, 0,
                                new String[]{message},
                                new String[]{message},
                                ProblemSeverities.Error,
                                startPosition,
                                endPosition,
                                lineNum+line-1, -1)
        compiler.problemReporter.record(problem, result, unit, true)
    }

    @Override
    void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {
    }

    @Override
    void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {
    }

    @Override
    void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {
    }

    @Override
    void error(int start, int end, String message) {
        report(1, message, start, end)
    }

    @Override
    void warn(int start, int end, String message) {
        report(0, message, start, end)
    }

    private void report(int severity, String message, int offset, int endOffset) {
        errorcount++
        def result = unit.compilationResult()
        char[] fileName = result.fileName
        int[] lineEnds = result.getLineSeparatorPositions()
        int startPosition
        int endPosition
        if (node!=null) {
            startPosition = node.sourceStart + offset
            endPosition = endOffset < 0 ?
                    node.sourceEnd - 1 :
                    node.sourceStart + endOffset
        }
        else {
            startPosition = 0
            endPosition = 0
        }
        int lineNumber = startPosition >= 0 ?
                getLineNumber(startPosition, lineEnds, 0, lineEnds.length - 1) : 0
        int columnNumber = startPosition >= 0 ?
                searchColumnNumber(lineEnds, lineNumber, startPosition) : 0
        String[] args = [message]
        def problem =
                compiler.problemReporter.problemFactory.createProblem(
                        fileName, 0,
                        args, args, severity,
                        startPosition, endPosition,
                        lineNumber, columnNumber)
        compiler.problemReporter.record(problem, result, unit, true)
    }

    static int getLineNumber(int position, int[] lineEnds, int g, int d) {
        if (lineEnds == null)
            return 1
        if (d == -1)
            return 1
        int m = g, start
        while (g <= d) {
            m = g + (d - g) / 2
            if (position < (start = lineEnds[m])) {
                d = m - 1
            }
            else if (position > start) {
                g = m + 1
            }
            else {
                return m + 1
            }
        }
        if (position < lineEnds[m]) {
            return m + 1
        }
        return m + 2
    }

    static int searchColumnNumber(int[] startLineIndexes, int lineNumber, int position) {
        switch (lineNumber) {
            case 1:
                return position + 1
            case 2:
                return position - startLineIndexes[0]
            default:
                int line = lineNumber - 2
                int length = startLineIndexes.length
                if (line >= length) {
                    return position - startLineIndexes[length - 1]
                }
                return position - startLineIndexes[line]
        }
    }

}
