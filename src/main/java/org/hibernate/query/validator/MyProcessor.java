package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import java.util.Set;

@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class MyProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element ee : element.getEnclosedElements()) {
                        checkHQL(ee);
                    }
                } else {
                    checkHQL(element);
                }
            }
        }
        return true;
    }

    private void checkHQL(Element element) {
        Elements elementUtils = processingEnv.getElementUtils();
        if (elementUtils instanceof JavacElements) {
            JavacProcessingEnvironment env = (JavacProcessingEnvironment) processingEnv;
            JCTree tree = ((JavacElements) elementUtils).getTree(element);
            if (tree != null) {
                tree.accept(new TreeScanner() {
                    @Override
                    public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
                        Name name = getMethodName(jcMethodInvocation.getMethodSelect());
                        if (name != null && name.toString().equals("createQuery")) {
                            super.visitApply(jcMethodInvocation);
                        }
                    }

                    @Override
                    public void visitLiteral(JCTree.JCLiteral jcLiteral) {
                        if (jcLiteral.value instanceof String) {
                            String hql = (String) jcLiteral.value;

                            Log log = Log.instance(env.getContext());
                            Pair pair = JavacElements.instance(env.getContext())
                                    .getTreeAndTopLevel(element, null, null);
                            JavaFileObject sourcefile = pair == null ? null :
                                    ((JCTree.JCCompilationUnit) pair.snd).sourcefile;

                            HqlParser parser = new HqlParser(hql) {
                                public void reportError(RecognitionException e) {
                                    log.error(jcLiteral.pos + e.column, "proc.messager", e.getMessage());
                                }
                                public void reportError(String text) {
                                    log.error(jcLiteral, "proc.messager", text);
                                }
                                public void reportWarning(String text) {
                                    log.warning(jcLiteral, "proc.messager", text);
                                }
                            };

                            JavaFileObject source = null;
                            if (sourcefile != null) {
                                source = log.useSource(sourcefile);
                            }
                            try {
                                parser.statement();
                            }
                            catch (Exception e) {}
                            finally {
                                if (sourcefile != null) {
                                    log.useSource(source);
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private static Name getMethodName(ExpressionTree select) {
        if (select instanceof MemberSelectTree) {
            MemberSelectTree ref = (MemberSelectTree) select;
            return ref.getIdentifier();
        } else if (select instanceof IdentifierTree) {
            IdentifierTree ref = (IdentifierTree) select;
            return ref.getName();
        } else {
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}