# Hibernate Query Validator

Compile time validation for queries written in HQL and JPQL.

*WARNING: this project is still at an experimental stage!*

## Building

Type `mvn install` from the project directory.

This will produce an artifact with the Maven coordinates 
`org.hibernate:query-validator:1.0-SNAPSHOT`.

## Usage

The persistent entity classes *must* be annotated with the basic
JPA metadata annotations like `@Entity`, `@ManyToOne`, 
`@Embeddable`, `@MappedSuperclass`, `@ElementCollection`, and 
`@Access`. You *may* use XML-based mappings to specify database 
mapping information like table and column names if that's what you 
prefer.

1. Put `query-validator-1.0-SNAPSHOT.jar` and its dependencies in 
   the compile-time classpath of your project.
2. Annotate a package or class with the `@CheckHQL` annotation.

Then any static string argument to the `createQuery()` method call
occurring in the annotated package or class will be validated, and 
a compile-time error produced if the query has syntax errors or if 
an identifier in the query doesn't reference a persistent entity or 
persistent field.

Note that the query is checked according to Hibernate's flavor of
JPQL (i.e. HQL), which is a superset of the query language defined 
by the JPA specification. In future, an optional "strict" mode will 
produce errors for non-spec-compliant queries.

## Usage from command line

Just compile your code with `javac` or `mvn`, with the query validator
and its dependencies in the compile-time classpath.

## Usage in IntelliJ

Select 'Enable annotation processing' in IntelliJ IDEA preferences 
under 'Build, Execution, Deployment > Compiler > AnnotationProcessors'. 

## Usage in Eclipse

The Eclipse compiler is not yet supported.
