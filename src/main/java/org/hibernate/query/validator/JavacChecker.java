package org.hibernate.query.validator;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL;

/**
 * @author Gavin King
 */
public class JavacChecker {
	private final JavacProcessor javacProcessor;

	public JavacChecker(JavacProcessor javacProcessor) {
		this.javacProcessor = javacProcessor;
	}

	ProcessingEnvironment getProcessingEnv() {
		return javacProcessor.getProcessingEnv();
	}

	JavacProcessor getJavacProcessor() {
		return javacProcessor;
	}

	void checkHQL(Element element) {
		Elements elementUtils = getProcessingEnv().getElementUtils();
		if (isCheckable(element) || isCheckable(element.getEnclosingElement())) {
//			List<String> whitelist = getWhitelist(element);
			JCTree tree = ((JavacElements) elementUtils).getTree(element);
			TypeElement panacheEntity = PanacheUtils.isPanache(element, getProcessingEnv().getTypeUtils(), elementUtils);
			if (tree != null) {
				tree.accept(new JavacTreeScanner(this, element, panacheEntity));
			}
		}
	}

	private static boolean isCheckAnnotation(AnnotationMirror am) {
		return am.getAnnotationType().asElement().toString().equals(CHECK_HQL);
	}

	private static boolean isCheckable(Element element) {
		for (AnnotationMirror am: element.getAnnotationMirrors()) {
			if (isCheckAnnotation(am)) {
				return true;
			}
		}
		return false;
	}

//	private List<String> getWhitelist(Element element) {
//		List<String> list = new ArrayList<>();
//		element.getAnnotationMirrors().forEach(am -> {
//			if (isCheckAnnotation(am)) {
//				am.getElementValues().forEach((var, act) -> {
//					switch (var.getSimpleName().toString()) {
//						case "whitelist":
//							if (act instanceof Attribute.Array) {
//								for (Attribute a: ((Attribute.Array) act).values) {
//									Object value = a.getValue();
//									if (value instanceof String) {
//										list.add(value.toString());
//									}
//								}
//							}
//							break;
//						case "dialect":
//							if (act instanceof Attribute.Class) {
//								String name = act.getValue().toString().replace(".class","");
//								list.addAll(MockSessionFactory.functionRegistry.getValidFunctionKeys());
//							}
//							break;
//					}
//				});
//			}
//		});
//		return list;
//	}

}
