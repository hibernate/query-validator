package org.hibernate.query.validator;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * <p>
 * Indicates that a package or toplevel type contains HQL
 * or JPQL queries encoded as static strings that should
 * be validated at compile time.
 * </p>
 *
 * <p>
 * Within a scope annotated {@code @CheckHQL}, any static
 * string argument to
 * </p>
 *
 * <ul>
 * <li>{@link javax.persistence.EntityManager#createQuery(String,Class)},
 * <li>{@link javax.persistence.EntityManager#createQuery(String)},
 * <li>{@link org.hibernate.Session#createQuery(String,Class)},
 * <li>{@link org.hibernate.Session#createQuery(String)}, or
 * <li>{@link javax.persistence.NamedQuery#query()}
 * </ul>
 *
 * <p>
 * is interpreted as HQL/JPQL and validated. Errors in the
 * query are reported by the Java compiler.
 * </p>
 *
 * <p>
 * The entity classes referred to in the queries must be
 * annotated with basic JPA metadata annotations like
 * {@code @Entity}, {@code @ManyToOne}, {@code @Embeddable},
 * {@code @MappedSuperclass}, {@code @ElementCollection},
 * and {@code @Access}. Metadata specified in XML mapping
 * documents is ignored by the query validator.
 * </p>
 *
 * <p>
 * Unknown entity and entity member names result in errors.
 * Unknown function names result in warnings which may be
 * suppressed using
 * {@code @SuppressWarnings("hql.unknown-function")}.
 * </p>
 *
 * @see SuppressWarnings
 * @see javax.persistence.NamedQuery
 * @see javax.persistence.EntityManager#createQuery(String)
 */
@Target({PACKAGE, TYPE})
@Retention(CLASS)
public @interface CheckHQL {}