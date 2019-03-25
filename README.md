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

Then any static string argument of

- the `createQuery()` method or
- the `@NamedQuery()` annotation

which occurs in the annotated package or class will be validated. 

A compile-time error is produced if the query has syntax errors or 
if an entity name or member name in the query doesn't reference a 
persistent entity or mapped field or property.

### Strict mode

By default, the query validator functions in "strict" mode, and 
produces errors for use of function names which aren't defined by the 
JPA specification or by HQL. This means that use of a user-defined or 
vendor-specific SQL function results in an error.

Strict mode may be disabled using `@CheckHQL(strict=false)`. Then any
unknown function will be ignored by the query validator.

### Usage from command line

Just compile your code with `javac` or `mvn`, with the query validator
and its dependencies in the compile-time classpath.

### Usage in IntelliJ

Select 'Enable annotation processing' in IntelliJ IDEA preferences 
under 'Build, Execution, Deployment > Compiler > AnnotationProcessors'. 

### Usage in Eclipse

The Eclipse compiler is not yet supported, but work will start on this 
soon.

## Compatibility

The query validator was developed and tested with:

- JDK 1.8.0_92
- Hibernate 5.4.2.Final

Other versions of `javac` and Hibernate may or may not work.

## Caveats

Please be aware of the following issues.

### HQL is a superset of JPQL

Quieries are interpreted according to Hibernate's flavor of JPQL 
(i.e. HQL), which is a superset of the query language defined by 
the JPA specification. 

In future, the "strict" mode will produce errors for queries 
which are not well-formed according to the JPA specification.

### Function arguments are not checked

Hibernate's query translater never typechecks function arguments 
and instead simply passes anything which looks like it might be a 
function call straight through to the database.

Fixing this will require a nontrivial enhancement to Hibernate's
HQL translator.

### Some ugly error messages

Sometimes Hibernate's HQL parser produces ugly error messages,
which are passed through by the query validator.

Fixing this requires a new release of Hibernate.
