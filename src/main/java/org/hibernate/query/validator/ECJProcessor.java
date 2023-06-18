package org.hibernate.query.validator;

import static org.hibernate.query.validator.ECJSessionFactory.getAnnotation;
import static org.hibernate.query.validator.ECJSessionFactory.qualifiedName;
import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

/**
 * Annotation processor that validates HQL and JPQL queries
 * for ECJ.
 *
 * @see org.hibernate.annotations.processing.CheckHQL
 *
 * @author Gavin King
 */
//@SupportedAnnotationTypes(CHECK_HQL)
public class ECJProcessor extends AbstractProcessor {

    static Mocker<ECJSessionFactory> sessionFactory = Mocker.variadic(ECJSessionFactory.class);

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Hibernate Query Validator for ECJ");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Compiler compiler = ((BaseProcessingEnvImpl) processingEnv).getCompiler();
        if (!roundEnv.getRootElements().isEmpty()) {
            for (CompilationUnitDeclaration unit : compiler.unitsToProcess) {
                compiler.parser.getMethodBodies(unit);
                checkHQL(unit, compiler);
            }
        }
        return true;
    }

    private void checkHQL(CompilationUnitDeclaration unit, Compiler compiler) {
        for (TypeDeclaration type : unit.types) {
            if (isCheckable(type.binding, unit)) {
//                List<String> whitelist = getWhitelist(type.binding, unit, compiler);
                Elements elements = processingEnv.getElementUtils();
                TypeElement typeElement = elements.getTypeElement(qualifiedName(type.binding));
                TypeElement panacheEntity = PanacheUtils.isPanache(typeElement, processingEnv.getTypeUtils(), elements);
                type.traverse(new ECJASTVisitor(panacheEntity, unit, compiler), unit.scope);
            }
        }
    }

    private static boolean isCheckable(TypeBinding type, CompilationUnitDeclaration unit) {
        return getCheckAnnotation(type, unit)!=null;
    }
//
//    private static List<String> getWhitelist(TypeBinding type,
//                                             CompilationUnitDeclaration unit,
//                                             Compiler compiler) {
//        ElementValuePair[] members =
//                getCheckAnnotation(type, unit).getElementValuePairs();
//        if (members==null || members.length==0) {
//            return emptyList();
//        }
//        List<String> names = new ArrayList<>();
//        for (ElementValuePair pair: members) {
//            Object value = pair.value;
//            if (value instanceof Object[]) {
//                for (Object literal : (Object[]) value) {
//                    if (literal instanceof StringConstant) {
//                        names.add(((StringConstant) literal).stringValue());
//                    }
//                }
//            }
//            else if (value instanceof StringConstant) {
//                names.add(((StringConstant) value).stringValue());
//            }
//            else if (value instanceof BinaryTypeBinding) {
////                String name = qualifiedName((BinaryTypeBinding) value);
//                names.addAll(MockSessionFactory.functionRegistry.getValidFunctionKeys());
//            }
//        }
//        return names;
//    }

    private static AnnotationBinding getCheckAnnotation(TypeBinding type,
                                                        CompilationUnitDeclaration unit) {
        AnnotationBinding result = getAnnotation(type, CHECK_HQL);
        if (result!=null) return result;
        Binding packInfo = unit.scope.getType("package-info".toCharArray());
        return getAnnotation(packInfo, CHECK_HQL);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

}
