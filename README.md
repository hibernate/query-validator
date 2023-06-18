![Hibernate logo][]

# Hibernate Query Validator

Compile time validation for queries written in HQL, JPQL, and 
[Panache][].

[Panache]: https://quarkus.io/guides/hibernate-orm-panache
[Hibernate logo]: http://static.jboss.org/hibernate/images/hibernate_logo_whitebkg_200px.png

## Requirements

This project now requires at least JDK 11, but JDK 15 or above 
is preferred.

## Building

Type `./gradlew` from this project directory.

This produces an artifact with the Maven coordinates 
`org.hibernate:query-validator:2.0-SNAPSHOT` in your local
Maven repository.

It also creates a far jar `query-validator-2.0-SNAPSHOT-all.jar`
in the `build/libs` directory of this project.

### Temporary requirement

Temporarily, you'll need to build a snapshot of Hibernate ORM 
6.3 from the `main` branch of the `hibernate-orm` project.

## Usage

The persistent entity classes *must* be annotated with the 
basic JPA metadata annotations like `@Entity`, `@ManyToOne`, 
`@Embeddable`, `@MappedSuperclass`, `@ElementCollection`, and 
`@Access`. You *may* use XML-based mappings to specify database 
mapping information like table and column names if that's what 
you prefer. But entities mapped *completely* in XML will not be 
discovered by the query validator.

1. Put `query-validator-2.0-SNAPSHOT-all.jar` in the 
   compile-time classpath of your project. (Or depend on
   `org.hibernate:query-validator:2.0-SNAPSHOT`.)
2. Annotate a package or toplevel class with `@CheckHQL`.

#### Usage with plain Hibernate or JPA

The validator will check any static string argument of

- the `createQuery()`, `createSelectionQuery()`, and 
  `createMutationQuery()` methods,
- the `@NamedQuery()` annotation, or
- the `@HQL` annotation

which occurs in a package, class, or interface annotated 
`@CheckHQL`. 

#### Usage with Panache

Inside a Panache entity or repository, the following queries 
will be checked:

- `list()`, `find()`, and `stream()`,
- `count()`,
- `delete()`, and
- `update()`

### Errors and warnings

The purpose of the query validator is to detect erroneous 
query strings and query parameter bindings when the Java code 
is compiled, instead of at runtime when the query is executed.

#### Errors

A compile-time error is produced if:

- the query has syntax errors,
- an entity name in the query doesn't reference a persistent 
  entity class,
- a member name in the query doesn't reference a mapped field 
  or property of the entity, or
- there is some other typing error, for example, incorrect
  function argument types.

#### Warnings

Additionally, any JPA `Query` instance that is created and 
immediately invoked in a single expression will have its 
parameter bindings validated. A warning is produced if:

- the query string has a parameter with no argument specified 
  using `setParameter()`, or
- an argument is specified using `setParameter()`, but there 
  is no matching parameter in the query string.

All Panache queries have their parameters validated.

### Usage from command line

When using a command line compiler, `gradle`, or `mvn`, errors 
from the query validator are displayed in the compiler output 
alongside other compilation errors.

#### `javac` and ECJ

Just compile your code with `javac`, or even with ECJ
(`java -jar ecj-4.6.1.jar`), with the query validator `jar` in 
the classpath: 

    -classpath query-validator-2.0-SNAPSHOT-all.jar

Of course, you'll also need Hibernate core on the classpath.

#### Gradle

In principle, it's enough to declare dependencies on Hibernate core 
and on the query validator, just like this:

    dependencies {
        implementation 'org.hibernate.orm:hibernate-core:6.3.0-SNAPSHOT'
        annotationProcessor 'org.hibernate:query-validator:2.0-SNAPSHOT'
    }

Unfortunately, this often results in some quite annoying warnings 
from `javac`. Get rid of them by also declaring an `implementation`
dependency on the Query validator:

    dependencies {
        implementation 'org.hibernate:query-validator:2.0-SNAPSHOT'
        annotationProcessor 'org.hibernate:query-validator:2.0-SNAPSHOT'
        implementation 'org.hibernate:query-validator:2.0-SNAPSHOT'
    }

#### Maven

Maven handles annotation processors correctly. Just declare the 
dependency on the query validator:

    <dependencies>
        <dependency>
            <groupId>org.hibernate</groupId>
            <artifactId>query-validator</artifactId>
            <version>2.0-SNAPSHOT</version>
            <optional>true</optional>
        </dependency>
    <dependencies>

### Usage in IDEs

Both IntelliJ and Eclipse require that annotation processing
be explicitly enabled.

#### IntelliJ

Select **Enable annotation processing** in IntelliJ IDEA 
preferences under **Build, Execution, Deployment > Compiler > 
AnnotationProcessors**. 

![IntelliJ Screenshot 1](img/intellij-annotation-processors.png)

You do not need to do this if you're using Gradle to build
your project.

IntelliJ only runs annotation processors during a build (that
is, when you `Run` your code or explicitly `Build Project`). 
So you won't see errors in your Java editor as you're typing.

#### Eclipse

Eclipse IDE doesn't load annotation processors from the 
project classpath. So you'll need to add the query validator
manually.

1. In **Project > Properties** go to **Java Compiler > 
   Annotation Processing** and select **Enable annotation 
   processing**. 
2. Then go to **Java Compiler > Annotation Processing > 
   Factory Path** and click **Add External JARs...** and
   add `build/libs/query-validator-2.0-SNAPSHOT-all.jar` 
   from this project directory.

Your project properties should look like this:

![Eclipse Screenshot 1](img/eclipse-annotation-processors.png)
![Eclipse Screenshot 2](img/eclipse-annotation-factorypath.png)

Eclipse runs annotation processors during every incremental
build (that is, every time you `Save`), so you'll see errors
displayed inline in your Java editor.

![Eclipse Screenshot 3](img/eclipse-errors.png)

If the query validator doesn't run, please ensure that:

- Eclipse itself is running on a compatible JDK.
- Your project is set up to compile with a compatible Java
  compiler, and the compiler compliance level is set to at 
  least 1.8.  

## Compatibility

The query validator was developed and tested with:

- JDK 15, JDK 17, JDK 20
- Hibernate 6.3.0-SNAPSHOT
- ECJ 3.33.0
- Eclipse IDE with JDT Core 3.33.0

Other versions of `javac`, ECJ, and Hibernate may or may not 
work. The query validator depends on internal compiler APIs in 
`javac` and ECJ, and is therefore sensitive to changes in the 
compilers.

## Caveats

Please be aware of the following issues.

#### HQL is a superset of JPQL

Queries are interpreted according to Hibernate's flavor of JPQL 
(i.e. HQL), which is a superset of the query language defined by 
the JPA specification. Queries accepted by the query validator
may not execute correctly on other implementations of JPA.

#### Explicit entity names are not supported in Eclipse/ECJ

In ECJ, don't use `@Entity(name="Whatever")`, since, during an
incremental build, the processor won't be able to discover the
entity named `Whatever`. Just let the entity name default to
the name of the class.

#### Ugly error messages

Please report ugly, confusing, or badly-formatted error messages 
as bugs.
