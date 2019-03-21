package org.hibernate.query.validator;

import antlr.RecognitionException;
import com.google.auto.service.AutoService;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;
import org.hibernate.QueryException;
import org.hibernate.hql.internal.ast.HqlSqlWalker;
import org.hibernate.hql.internal.ast.QueryTranslatorImpl;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Queryable;
import org.hibernate.type.*;

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
import javax.persistence.Entity;
import javax.tools.JavaFileObject;
import java.util.Set;

import static java.util.Collections.emptyMap;

@SupportedAnnotationTypes("org.hibernate.query.validator.CheckHQL")
@AutoService(Processor.class)
public class HQLValidatingProcessor extends AbstractProcessor {

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
                    int errors = 0;

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

                            Context context = env.getContext();
                            Names names = Names.instance(context);
                            Symtab syms = Symtab.instance(context);

                            Log log = Log.instance(context);
                            Pair pair = JavacElements.instance(context)
                                    .getTreeAndTopLevel(element, null, null);
                            JavaFileObject sourcefile = pair == null ? null :
                                    ((JCTree.JCCompilationUnit) pair.snd).sourcefile;

                            //first parse with a special parser that correctly
                            //reports column numbers for syntax errors
                            HqlParser parser = new HqlParser(hql) {
                                public void reportError(RecognitionException e) {
                                    errors++;
                                    log.error(jcLiteral.pos + e.column, "proc.messager", e.getMessage());
                                }
                                public void reportError(String text) {
                                    errors++;
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

                                if (errors==0) {
                                    //now reparse and then transform with Hibernate's
                                    //parser and transformer, that don't correctly
                                    //report column numbers, but can do more stuff
                                    org.hibernate.hql.internal.ast.HqlParser parser2 =
                                            org.hibernate.hql.internal.ast.HqlParser.getInstance(hql);
                                    parser2.statement();
                                    DummySessionFactory factory = new DummySessionFactory() {

                                        private Type type(TypeSymbol symbol) {
                                            switch (symbol.asType().getTag()) {
                                                case BOOLEAN:
                                                    return BooleanType.INSTANCE;
                                                case SHORT:
                                                    return ShortType.INSTANCE;
                                                case INT:
                                                    return IntegerType.INSTANCE;
                                                case LONG:
                                                    return LongType.INSTANCE;
                                                case BYTE:
                                                    return ByteType.INSTANCE;
                                                case CHAR:
                                                    return CharacterType.INSTANCE;
                                                case FLOAT:
                                                    return FloatType.INSTANCE;
                                                case DOUBLE:
                                                    return DoubleType.INSTANCE;
                                                case CLASS:
                                                    switch (symbol.flatName().toString()) {
                                                        case "java.lang.String":
                                                            return StringType.INSTANCE;
                                                        //TODO: JDK wrapper types!!!
                                                    }
                                                    EntityPersister ep = entityPersister(symbol.name.toString());
                                                    if (ep instanceof Queryable) {
                                                        return ((Queryable) ep).getType();
                                                    }
                                            }
                                            //rubbishy default
                                            return StringType.INSTANCE;
                                        }

                                        @Override
                                        EntityPersister entityPersister(String entityName) {
                                            for (PackageSymbol pack: syms.packages.values()) {
                                                final Symbol type = lookup(names, pack, entityName);
                                                if (type != null && type.getAnnotation(Entity.class) != null) {
                                                    return new DummyEntityPersister(entityName, this) {
                                                        @Override
                                                        public Type toType(String name) throws QueryException {
                                                            String[] segments = name.split("\\.");
                                                            TypeSymbol memberType = type.type.tsym;
                                                            for (String segment: segments) {
                                                                Symbol member = lookup(names, memberType, segment);
                                                                if (member == null) {
                                                                    return null;
                                                                }
                                                                else {
                                                                    memberType = member.type.tsym;
                                                                }
                                                            }
                                                            return type(memberType);
                                                        }

                                                        @Override
                                                        public Type getIdentifierType() {
                                                            return StandardBasicTypes.INTEGER;
                                                        }

                                                        @Override
                                                        public String getIdentifierPropertyName() {
                                                            return "id";
                                                        }
                                                    };
                                                }
                                            }
                                            return null;
                                        }
                                    };

                                    QueryTranslatorImpl queryTranslator =
                                            new QueryTranslatorImpl(null, hql, emptyMap(), factory);
                                    new HqlSqlWalker(queryTranslator, factory, parser2, emptyMap(), null) {
                                        public void reportError(RecognitionException e) {
                                            errors++;
                                            log.error(jcLiteral.pos + e.column, "proc.messager", e.getMessage());
                                        }
                                        public void reportError(String text) {
                                            errors++;
                                            log.error(jcLiteral, "proc.messager", text);
                                        }
                                        public void reportWarning(String text) {
                                            log.warning(jcLiteral, "proc.messager", text);
                                        }
                                    }.statement(parser2.getAST());

                                    //don't use this much simpler implementation
                                    //because it does to much stuff (generates SQL)
                                    //  queryTranslator.compile(null, false);
                                }

                            }
                            catch (Exception e) {
                                log.error(jcLiteral, "proc.messager", e.getMessage());
                            }
                            finally {
                                errors = 0;
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

    private static Symbol lookup(Names names, Symbol p, String name) {
        return p.members().lookup(names.fromString(name)).sym;
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