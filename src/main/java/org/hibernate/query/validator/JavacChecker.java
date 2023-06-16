package org.hibernate.query.validator;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.HashSet;
import java.util.Set;

import static org.hibernate.query.validator.HQLProcessor.CHECK_HQL;
import static org.hibernate.query.validator.HQLProcessor.hibernate;
import static org.hibernate.query.validator.HQLProcessor.jpa;
import static org.hibernate.query.validator.Validation.validate;

/**
 * @author Gavin King
 */
public class JavacChecker {
	private final JavacProcessor javacProcessor;

	public JavacChecker(JavacProcessor javacProcessor) {
		this.javacProcessor = javacProcessor;
	}

	void checkHQL(Element element) {
		Elements elementUtils = javacProcessor.getProcessingEnv().getElementUtils();
		if (isCheckable(element) || isCheckable(element.getEnclosingElement())) {
//			List<String> whitelist = getWhitelist(element);
			JCTree tree = ((JavacElements) elementUtils).getTree(element);
			TypeElement panacheEntity = PanacheUtils.isPanache(element, javacProcessor.getProcessingEnv().getTypeUtils(), elementUtils);
			if (tree != null) {
				tree.accept(new TreeScanner() {
					final Set<Integer> setParameterLabels = new HashSet<>();
					final Set<String> setParameterNames = new HashSet<>();
					final Set<String> setOrderBy = new HashSet<>();
					boolean immediatelyCalled;

					private void check(JCTree.JCLiteral jcLiteral, String hql,
									   boolean inCreateQueryMethod) {
						ErrorReporter handler = new ErrorReporter(javacProcessor, jcLiteral, element, hql);
						validate(hql, inCreateQueryMethod && immediatelyCalled,
								setParameterLabels, setParameterNames, handler,
								JavacProcessor.sessionFactory.make(javacProcessor.getProcessingEnv()));
					}

					private void checkPanacheQuery(JCTree.JCLiteral jcLiteral, String targetType, String methodName, String panacheQl,
									   com.sun.tools.javac.util.List<JCTree.JCExpression> args) {
						ErrorReporter handler = new ErrorReporter(javacProcessor, jcLiteral, element, panacheQl);
						collectPanacheArguments(args);
						int[] offset = new int[1];
						String hql = PanacheUtils.panacheQlToHql(handler, targetType, methodName,
																 panacheQl, offset, setParameterLabels, setOrderBy);
						if (hql == null)
							return;
						validate(hql, true,
								setParameterLabels, setParameterNames, handler,
								JavacProcessor.sessionFactory.make(javacProcessor.getProcessingEnv()),
								offset[0]);
					}

					private void collectPanacheArguments(com.sun.tools.javac.util.List<JCTree.JCExpression> args) {
						// first arg is pql
						// second arg can be Sort, Object..., Map or Parameters
						setParameterLabels.clear();
						setParameterNames.clear();
						setOrderBy.clear();
						com.sun.tools.javac.util.List<JCTree.JCExpression> nonQueryArgs = args.tail;
						if (!nonQueryArgs.isEmpty()) {
							if (isSortCall(nonQueryArgs.head)) {
								nonQueryArgs = nonQueryArgs.tail;
							}

							if (!nonQueryArgs.isEmpty()) {
								JCTree.JCExpression firstArg = nonQueryArgs.head;
								isParametersCall(firstArg);
								if (setParameterNames.isEmpty()) {
									int i = 1;
									for (JCTree.JCExpression arg : nonQueryArgs) {
										setParameterLabels.add(i++);
									}
								}
							}
						}
					}

					private boolean isParametersCall(JCTree.JCExpression firstArg) {
						if (firstArg.getKind() == Tree.Kind.METHOD_INVOCATION) {
							JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation)firstArg;
							JCTree.JCExpression method = invocation.meth;
							if (method.getKind() == Tree.Kind.MEMBER_SELECT) {
								JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) method;
								if (fa.name.toString().equals("and") && isParametersCall(fa.selected)) {
									JCTree.JCLiteral queryArg = firstArgument(invocation);
									if (queryArg != null && queryArg.value instanceof String) {
										String name = (String) queryArg.value;
										setParameterNames.add(name);
										return true;
									}
								}
								else if (fa.name.toString().equals("with")
										&& fa.selected.getKind() == Tree.Kind.IDENTIFIER) {
									String target = ((JCTree.JCIdent)fa.selected).name.toString();
									if (target.equals("Parameters")) {
										JCTree.JCLiteral queryArg = firstArgument(invocation);
										if (queryArg != null && queryArg.value instanceof String) {
											String name = (String) queryArg.value;
											setParameterNames.add(name);
											return true;
										}
									}
								}
							}
						}
						return false;
					}

					private boolean isSortCall(JCTree.JCExpression firstArg) {
						if (firstArg.getKind() == Tree.Kind.METHOD_INVOCATION) {
							JCTree.JCMethodInvocation invocation = (JCTree.JCMethodInvocation)firstArg;
							JCTree.JCExpression method = invocation.meth;
							if (method.getKind() == Tree.Kind.MEMBER_SELECT) {
								JCTree.JCFieldAccess fa = (JCTree.JCFieldAccess) method;
								String fieldName = fa.name.toString();
								if ((fieldName.equals("and")
										|| fieldName.equals("descending")
										|| fieldName.equals("ascending")
										|| fieldName.equals("direction"))
										&& isSortCall(fa.selected)) {
									for (JCTree.JCExpression e : invocation.args) {
										if (e instanceof JCTree.JCLiteral) {
											JCTree.JCLiteral lit = (JCTree.JCLiteral)e;
											if (lit.value instanceof String) {
												setOrderBy.add((String)lit.value);
											}
										}
									}
									return true;
								}
								else if ((fieldName.equals("by")
										|| fieldName.equals("descending")
										|| fieldName.equals("ascending"))
										&& fa.selected.getKind() == Tree.Kind.IDENTIFIER) {
									String target = ((JCTree.JCIdent)fa.selected).name.toString();
									if (target.equals("Sort")) {
										for (JCTree.JCExpression e : invocation.args) {
											if (e instanceof JCTree.JCLiteral) {
												JCTree.JCLiteral lit = (JCTree.JCLiteral)e;
												if (lit.value instanceof String) {
													setOrderBy.add((String)lit.value);
												}
											}
										}
										return true;
									}
								}
							}
						}
						return false;
					}

					JCTree.JCLiteral firstArgument(JCTree.JCMethodInvocation call) {
						for (JCTree.JCExpression e : call.args) {
							return e instanceof JCTree.JCLiteral ?
									(JCTree.JCLiteral) e : null;
						}
						return null;
					}

					@Override
					public void visitApply(JCTree.JCMethodInvocation jcMethodInvocation) {
						String name = getMethodName(jcMethodInvocation.meth);
						switch (name) {
							case "getResultList":
							case "getSingleResult":
							case "getSingleResultOrNull":
								immediatelyCalled = true;
								super.visitApply(jcMethodInvocation);
								immediatelyCalled = false;
								break;
							case "count":
							case "delete":
							case "update":
							case "exists":
							case "stream":
							case "list":
							case "find":
								switch (jcMethodInvocation.meth.getKind()) {
								// disable this until we figure out how to type the LHS
//                                case MEMBER_SELECT:
//                                    JCTree.JCFieldAccess fa = (JCFieldAccess) jcMethodInvocation.meth;
//                                    switch (fa.selected.getKind()) {
//                                    case IDENTIFIER:
//                                        JCTree.JCIdent target = (JCIdent) fa.selected;
//                                        JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
//                                        if (queryArg != null && queryArg.value instanceof String) {
//                                            String panacheQl = (String) queryArg.value;
//                                            checkPanacheQuery(queryArg, target.name.toString(), name, panacheQl, jcMethodInvocation.args);
//                                        }
//                                        break;
//                                    }
//                                    break;
								case IDENTIFIER:
									JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
									if (queryArg != null && queryArg.value instanceof String && panacheEntity != null) {
										String panacheQl = (String) queryArg.value;
										checkPanacheQuery(queryArg, panacheEntity.getSimpleName().toString(), name, panacheQl, jcMethodInvocation.args);
									}
									break;
								}
								super.visitApply(jcMethodInvocation); //needed!
								break;
							case "createQuery":
							case "createSelectionQuery":
							case "createMutationQuery":
								JCTree.JCLiteral queryArg = firstArgument(jcMethodInvocation);
								if (queryArg != null && queryArg.value instanceof String) {
									String hql = (String) queryArg.value;
									check(queryArg, hql, true);
								}
								super.visitApply(jcMethodInvocation);
								break;
							case "setParameter":
								JCTree.JCLiteral paramArg = firstArgument(jcMethodInvocation);
								if (paramArg != null) {
									if (paramArg.value instanceof String) {
										setParameterNames.add((String) paramArg.value);
									}
									else if (paramArg.value instanceof Integer) {
										setParameterLabels.add((Integer) paramArg.value);
									}
								}
								super.visitApply(jcMethodInvocation);
								break;
							default:
								super.visitApply(jcMethodInvocation); //needed!
								break;
						}
					}

					@Override
					public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
						AnnotationMirror annotation = jcAnnotation.attribute;
						String name = annotation.getAnnotationType().toString();
						if (name.equals(jpa("NamedQuery")) || name.equals(hibernate("NamedQuery"))) {
							for (JCTree.JCExpression arg : jcAnnotation.args) {
								if (arg instanceof JCTree.JCAssign) {
									JCTree.JCAssign assign = (JCTree.JCAssign) arg;
									if ("query".equals(assign.lhs.toString())
											&& assign.rhs instanceof JCTree.JCLiteral) {
										JCTree.JCLiteral jcLiteral =
												(JCTree.JCLiteral) assign.rhs;
										if (jcLiteral.value instanceof String) {
											check(jcLiteral, (String) jcLiteral.value, false);
										}
									}
								}
							}
						}
						else if (name.equals(hibernate("Hql"))) {
							for (JCTree.JCExpression arg : jcAnnotation.args) {
								if (arg instanceof JCTree.JCAssign) {
									JCTree.JCAssign assign = (JCTree.JCAssign) arg;
									if ("value".equals(assign.lhs.toString())
											&& assign.rhs instanceof JCTree.JCLiteral) {
										JCTree.JCLiteral jcLiteral =
												(JCTree.JCLiteral) assign.rhs;
										if (jcLiteral.value instanceof String) {
											check(jcLiteral, (String) jcLiteral.value, false);
										}
									}
								}
							}
						}
						else {
							super.visitAnnotation(jcAnnotation); //needed!
						}
					}

				});
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

	private static String getMethodName(ExpressionTree select) {
		if (select instanceof MemberSelectTree) {
			MemberSelectTree ref = (MemberSelectTree) select;
			return ref.getIdentifier().toString();
		}
		else if (select instanceof IdentifierTree) {
			IdentifierTree ref = (IdentifierTree) select;
			return ref.getName().toString();
		}
		else {
			return null;
		}
	}

}
