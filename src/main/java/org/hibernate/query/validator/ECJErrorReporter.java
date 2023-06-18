package org.hibernate.query.validator;

import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities;

import java.util.BitSet;

import static org.eclipse.jdt.internal.compiler.util.Util.getLineNumber;
import static org.eclipse.jdt.internal.compiler.util.Util.searchColumnNumber;
import static org.hibernate.query.hql.internal.StandardHqlTranslator.prettifyAntlrError;

/**
 * @author Gavin King
 */
class ECJErrorReporter implements Validation.Handler {

    private final ASTNode node;
    private final CompilationUnitDeclaration unit;
    private final Compiler compiler;
    private final String hql;
    private int errorcount;

    ECJErrorReporter(ASTNode node,
                     CompilationUnitDeclaration unit,
                     Compiler compiler,
                     String hql) {
        this.node = node;
        this.unit = unit;
        this.compiler = compiler;
        this.hql = hql;
    }

    @Override
    public int getErrorCount() {
        return errorcount;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object symbol, int line, int charInLine, String message, RecognitionException e) {
        message = prettifyAntlrError(symbol, line, charInLine, message, e, hql, false);
        errorcount++;
        CompilationResult result = unit.compilationResult();
        char[] fileName = result.fileName;
        int[] lineEnds = result.getLineSeparatorPositions();
        int startIndex;
        int stopIndex;
        int lineNum = getLineNumber(node.sourceStart, lineEnds, 0, lineEnds.length - 1);
        Token offendingToken = e.getOffendingToken();
        if (offendingToken != null) {
            startIndex = offendingToken.getStartIndex();
            stopIndex = offendingToken.getStopIndex();
        } else if (e instanceof LexerNoViableAltException) {
            startIndex = ((LexerNoViableAltException) e).getStartIndex();
            stopIndex = startIndex;
        } else {
            startIndex = lineEnds[line - 1] + charInLine;
            stopIndex = startIndex;
        }
        int startPosition = node.sourceStart + startIndex + 1;
        int endPosition = node.sourceStart + stopIndex + 1;
        if (endPosition < startPosition) {
            endPosition = startPosition;
        }
        CategorizedProblem problem =
                compiler.problemReporter.problemFactory
                        .createProblem(fileName, 0,
                                new String[]{message},
                                new String[]{message},
                                ProblemSeverities.Error,
                                startPosition,
                                endPosition,
                                lineNum + line - 1, -1);
        compiler.problemReporter.record(problem, result, unit, true);
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

    @Override
    public void error(int start, int end, String message) {
        report(ProblemSeverities.Error, message, start, end);
    }

    @Override
    public void warn(int start, int end, String message) {
        report(ProblemSeverities.Warning, message, start, end);
    }

    private void report(int severity, String message, int offset, int endOffset) {
        errorcount++;
        CompilationResult result = unit.compilationResult();
        char[] fileName = result.fileName;
        int[] lineEnds = result.getLineSeparatorPositions();
        int startPosition;
        int endPosition;
        if (node != null) {
            startPosition = node.sourceStart + offset;
            endPosition = endOffset < 0 ?
                    node.sourceEnd - 1 :
                    node.sourceStart + endOffset;
        } else {
            startPosition = 0;
            endPosition = 0;
        }
        int lineNumber = startPosition >= 0
                ? getLineNumber(startPosition, lineEnds, 0, lineEnds.length - 1)
                : 0;
        int columnNumber = startPosition >= 0
                ? searchColumnNumber(lineEnds, lineNumber, startPosition)
                : 0;

        CategorizedProblem problem =
                compiler.problemReporter.problemFactory
                        .createProblem(fileName, 0,
                                new String[]{message},
                                new String[]{message},
                                severity,
                                startPosition, endPosition,
                                lineNumber, columnNumber);
        compiler.problemReporter.record(problem, result, unit, true);
    }
}
