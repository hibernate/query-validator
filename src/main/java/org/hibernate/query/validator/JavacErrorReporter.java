package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.ast.ParseErrorHandler;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

class JavacErrorReporter implements ParseErrorHandler {

    private Log log;
    private JavaFileObject source;
    private int errorCount = 0;
    private JCTree.JCLiteral jcLiteral;

    JavacErrorReporter(JCTree.JCLiteral jcLiteral, Element element,
                       ProcessingEnvironment processingEnv) {
        this.jcLiteral = jcLiteral;

        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        log = Log.instance(context);
        Pair pair = JavacElements.instance(context)
                .getTreeAndTopLevel(element, null, null);
        JavaFileObject sourcefile = pair == null ? null :
                ((JCTree.JCCompilationUnit) pair.snd).sourcefile;
        if (sourcefile != null) {
            source = log.useSource(sourcefile);
        }
    }

    @Override
    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public void throwQueryException() throws QueryException {}

    @Override
    public void reportError(RecognitionException e) {
        errorCount++;
        log.error(jcLiteral.pos + e.column, "proc.messager", e.getMessage());
    }

    @Override
    public void reportError(String text) {
        errorCount++;
        log.error(jcLiteral, "proc.messager", text);
    }

    @Override
    public void reportWarning(String text) {
        log.warning(jcLiteral, "proc.messager", text);
    }

}
