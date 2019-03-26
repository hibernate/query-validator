package org.hibernate.query.validator;

import antlr.NoViableAltException;
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
import java.util.regex.Matcher;

import static java.util.regex.Pattern.compile;

class JavacErrorReporter implements ParseErrorHandler {

    private Log log;
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
            log.useSource(sourcefile);
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
        if (errorCount>0 && e instanceof NoViableAltException) {
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
        log.error(jcLiteral.pos + e.column, "proc.messager", text);
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
        log.error(jcLiteral, "proc.messager", text);
    }

    @Override
    public void reportWarning(String text) {
        log.warning(jcLiteral, "proc.messager", text);
    }

}
