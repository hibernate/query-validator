package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import org.hibernate.QueryException;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import static com.sun.tools.javac.resources.CompilerProperties.Warnings.ProcMessager;

/**
 * @author Gavin King
 */
class ErrorReporter implements Validation.Handler {

	private static final String KEY = "proc.messager";

	private final Log log;
	private final JCTree.JCLiteral literal;

	ErrorReporter(JavacProcessor processor, JCTree.JCLiteral literal, Element element) {
		this.literal = literal;

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
		return 0;
	}

	@Override
	public void throwQueryException() throws QueryException {
	}

	@Override
	public void error(int start, int end, String message) {
		log.error(literal.pos + start, KEY, message);
	}

	@Override
	public void warn(int start, int end, String message) {

		log.warning(literal.pos + start, ProcMessager(message));
	}

	@Override
	public void reportError(RecognitionException e) {
		log.error(literal.pos + e.column, KEY, e.getMessage());
	}

	@Override
	public void reportError(String text) {
		log.error(literal.pos, KEY, text);
	}

	@Override
	public void reportWarning(String text) {
		log.warning(literal.pos, ProcMessager(text));
	}

}
