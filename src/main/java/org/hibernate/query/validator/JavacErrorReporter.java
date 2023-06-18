package org.hibernate.query.validator;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import java.util.BitSet;

import static com.sun.tools.javac.resources.CompilerProperties.Warnings.ProcMessager;
import static org.hibernate.query.hql.internal.StandardHqlTranslator.prettifyAntlrError;

/**
 * @author Gavin King
 */
class JavacErrorReporter implements Validation.Handler {

	private static final String KEY = "proc.messager";

	private final Log log;
	private final JCTree.JCLiteral literal;
	private final String hql;
	private int errorcount;

	JavacErrorReporter(JavacProcessor processor, JCTree.JCLiteral literal, Element element, String hql) {
		this.literal = literal;
		this.hql = hql;

		Context context = processor.getContext();
		log = Log.instance(context);
		Pair<JCTree, JCTree.JCCompilationUnit> pair =
				JavacElements.instance(context).getTreeAndTopLevel(element, null, null);
		JavaFileObject sourcefile = pair == null ? null : pair.snd.sourcefile;
		if (sourcefile != null) {
			log.useSource(sourcefile);
		}
	}

	@Override
	public int getErrorCount() {
		return errorcount;
	}

	@Override
	public void error(int start, int end, String message) {
		errorcount++;
		log.error(literal.pos + start, KEY, message);
	}

	@Override
	public void warn(int start, int end, String message) {
		log.warning(literal.pos + start, ProcMessager(message));
//		log.error(literal.pos + start, KEY, message);
	}

	@Override
	public void syntaxError(
			Recognizer<?, ?> recognizer,
			Object offendingSymbol,
			int line,
			int charPositionInLine,
			String message,
			RecognitionException e) {
		message = prettifyAntlrError(offendingSymbol, line, charPositionInLine, message, e, hql, false);
		errorcount++;
		Token offendingToken = e.getOffendingToken();
		if ( offendingToken != null ) {
			log.error(literal.pos+1+offendingToken.getStartIndex(), KEY, message);
		}
		else if ( e instanceof LexerNoViableAltException ) {
			log.error(literal.pos+1+((LexerNoViableAltException) e).getStartIndex(), KEY, message);
		}
		else {
			log.error(literal.pos+1, KEY, message);
		}
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
