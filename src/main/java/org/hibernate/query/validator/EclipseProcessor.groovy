package org.hibernate.query.validator

import antlr.RecognitionException
import org.eclipse.jdt.core.compiler.CategorizedProblem
import org.eclipse.jdt.internal.compiler.ASTVisitor
import org.eclipse.jdt.internal.compiler.CompilationResult
import org.eclipse.jdt.internal.compiler.Compiler
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl
import org.eclipse.jdt.internal.compiler.ast.*
import org.eclipse.jdt.internal.compiler.lookup.BlockScope
import org.eclipse.jdt.internal.compiler.problem.ProblemSeverities
import org.hibernate.QueryException
import org.hibernate.hql.internal.ast.ParseErrorHandler

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

import static org.eclipse.jdt.core.compiler.CharOperation.charToString
import static org.eclipse.jdt.internal.compiler.util.Util.getLineNumber
import static org.eclipse.jdt.internal.compiler.util.Util.searchColumnNumber
import static org.hibernate.query.validator.EclipseSessionFactory.compiler
import static org.hibernate.query.validator.EclipseSessionFactory.qualifiedName
import static org.hibernate.query.validator.Validation.validate

/**
 * Annotation processor that validates HQL and JPQL queries
 * for Eclipse.
 *
 * @see CheckHQL
 */
//@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
//@AutoService(Processor.class)
class EclipseProcessor extends AbstractProcessor {

    @Override
    boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element instanceof PackageElement) {
                    for (Element root : roundEnv.getRootElements()) {
                        Element enclosing = root.getEnclosingElement()
                        if (enclosing!=null && enclosing==element) {
                            checkHQL(root)
                        }
                    }
                }
                else {
                    checkHQL(element)
                }
            }
        }
        return false
    }

    @Override
    synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv)
        EclipseSessionFactory.initialize((BaseProcessingEnvImpl) processingEnv)
    }

    private void checkHQL(Element element) {
        if (processingEnv instanceof BaseProcessingEnvImpl) {
            Compiler compiler =
                    ((BaseProcessingEnvImpl) processingEnv)
                            .getCompiler()
            for (CompilationUnitDeclaration unit: compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit)
                for (TypeDeclaration type: unit.types) {
                    if (qualifiedName(type.binding) == element.toString()) {
                        type.traverse(new ASTVisitor() {
                            boolean inCreateQueryMethod = false

                            @Override
                            boolean visit(MessageSend messageSend, BlockScope scope) {
                                inCreateQueryMethod =
                                        charToString(messageSend.selector) == "createQuery"
                                return true
                            }

                            @Override
                            void endVisit(MessageSend messageSend, BlockScope scope) {
                                inCreateQueryMethod = false
                            }

                            @Override
                            boolean visit(MemberValuePair pair, BlockScope scope) {
                                if (qualifiedName(pair.binding)
                                        == "javax.persistence.NamedQuery.query") {
                                    inCreateQueryMethod = true
                                }
                                return true
                            }

                            @Override
                            void endVisit(MemberValuePair pair, BlockScope scope) {
                                inCreateQueryMethod = false
                            }

                            @Override
                            void endVisit(StringLiteral stringLiteral, BlockScope scope) {
                                if (inCreateQueryMethod) {
                                    String hql = charToString(stringLiteral.source())

                                    ErrorReporter handler = new ErrorReporter(stringLiteral, unit)

                                    validate(hql, handler, new EclipseSessionFactory(handler))
                                }

                            }
                        }, unit.scope)
                    }
                }
            }

        }
    }

    @Override
    SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported()
    }

    class ErrorReporter implements ParseErrorHandler {

        private StringLiteral literal
        private CompilationUnitDeclaration unit

        ErrorReporter(StringLiteral literal,
                      CompilationUnitDeclaration unit) {
            this.literal = literal
            this.unit = unit
        }

        @Override
        int getErrorCount() {
            return 0
        }

        @Override
        void throwQueryException() throws QueryException {}

        private void report(int severity, String message, int offset) {
            CompilationResult result = unit.compilationResult()
            char[] fileName = result.fileName
            int[] lineEnds = result.getLineSeparatorPositions()
            int startPosition = literal.sourceStart + offset + 1
            int endPosition = literal.sourceEnd - 1 //search for the end of the token!
            int lineNumber = startPosition >= 0 ?
                    getLineNumber(startPosition, lineEnds, 0, lineEnds.length-1) : 0
            int columnNumber = startPosition >= 0 ?
                    searchColumnNumber(lineEnds, lineNumber, startPosition) : 0
            String[] args = [message]
            CategorizedProblem problem =
                    compiler.problemReporter.problemFactory
                            .createProblem(fileName, 0,
                            args, args,
                            severity,
                            startPosition, endPosition,
                            lineNumber, columnNumber)
            compiler.problemReporter.record(problem, result, unit, true)
        }

        @Override
        void reportError(RecognitionException e) {
            report(ProblemSeverities.Error, e.getMessage(), e.getColumn()-1)
        }

        @Override
        void reportError(String text) {
            report(ProblemSeverities.Error, text, 0)
        }

        @Override
        void reportWarning(String text) {
            report(ProblemSeverities.Warning, text, 0)
        }

    }

}
