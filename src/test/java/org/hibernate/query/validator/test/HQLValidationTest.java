package org.hibernate.query.validator.test;

import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static javax.tools.ToolProvider.getSystemJavaCompiler;
import static org.hibernate.query.validator.HQLProcessor.forceEclipseForTesting;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HQLValidationTest {

    private final Path TEST_LIBS = Paths.get("test-runtime-libs");

    @Test
    public void testJavac() throws Exception {
        String errors = compileWithJavac("test", "test.test");

        assertFalse(errors.contains("GoodQueries.java:"));
        assertFalse(errors.contains("PanachePerson.java:"));
        assertFalse(errors.contains("PanacheRepository.java:"));

        assertTrue(errors.contains("BadQueries.java:9: error: no viable alternative at input '*do'"));
        assertTrue(errors.contains("BadQueries.java:10: error: no viable alternative at input 'from"));
        assertTrue(errors.contains("BadQueries.java:11: error: no viable alternative at input '"));
        assertTrue(errors.contains("BadQueries.java:12: error: no viable alternative at input '"));
        assertTrue(errors.contains("BadQueries.java:13: error: Could not interpret path expression 'from"));

        assertTrue(errors.contains("BadQueries.java:15: error: Could not resolve class 'test.Nil' named for instantiation"));
//        assertTrue(errors.contains("BadQueries.java:16: error: test.Pair has no suitable constructor for types (Person)"));
//        assertTrue(errors.contains("BadQueries.java:17: error: test.Pair has no suitable constructor for types (Person, string)"));
//        assertTrue(errors.contains("BadQueries.java:53: error: test.Pair has no suitable constructor for types (integer, integer)"));
//        assertTrue(errors.contains("BadQueries.java:54: error: test.Pair has no suitable constructor for types (string, string)"));

        assertTrue(errors.contains("BadQueries.java:19: error: Could not resolve root entity 'People'"));
        assertTrue(errors.contains("BadQueries.java:20: error: Could not resolve attribute 'firstName' of 'Person'"));
        assertTrue(errors.contains("BadQueries.java:21: error: Could not resolve attribute 'addr' of 'Person'"));
        assertTrue(errors.contains("BadQueries.java:22: error: Could not resolve attribute 'town' of 'Address'"));
        assertTrue(errors.contains("BadQueries.java:23: error: Could not resolve attribute 'name' of 'Address'"));
        assertTrue(errors.contains("BadQueries.java:24: error: Could not resolve attribute 'type' of 'Country'"));

        assertTrue(errors.contains("BadQueries.java:26: error: Could not interpret attribute 'length' of basic-valued path"));
        assertTrue(errors.contains("BadQueries.java:27: error: Terminal path has no attribute 'length'"));

        assertTrue(errors.contains("BadQueries.java:29: error: Could not interpret path expression 'xxx'"));
//        assertTrue(errors.contains("BadQueries.java:30: warning: func is not defined"));
//        assertTrue(errors.contains("BadQueries.java:31: warning: custom is not defined"));
//        assertTrue(errors.contains("BadQueries.java:32: warning: p is not defined"));
        assertTrue(errors.contains("BadQueries.java:32: error: Could not interpret path expression 'p.name'"));

        //TODO re-enable!
//        assertTrue(errors.contains("BadQueries.java:34: error: key(), value(), or entry() argument must be map element"));
//        assertTrue(errors.contains("BadQueries.java:35: error: key(), value(), or entry() argument must be map element"));
//        assertTrue(errors.contains("BadQueries.java:36: error: key(), value(), or entry() argument must be map element"));

        assertTrue(errors.contains("BadQueries.java:39: error: mismatched input '.', expecting"));

        assertTrue(errors.contains("BadQueries.java:41: error: Unlabeled ordinal parameter ('?' rather than ?1)"));

        assertTrue(errors.contains("Person.java:22: error: Could not resolve attribute 'x' of 'Person'"));

        assertTrue(errors.contains("BadQueries.java:46: warning: Parameter ?2 is not set"));
        assertTrue(errors.contains("BadQueries.java:48: warning: Parameter :name is not set"));

        assertTrue(errors.contains("BadQueries.java:51: warning: Parameter :hello does not occur in the query"));

        assertTrue(errors.contains("BadQueries.java:56: error: token recognition error at: ''gavin'"));
        assertTrue(errors.contains("BadQueries.java:57: error: token recognition error at: ''gavin'"));

        assertTrue(errors.contains("BadQueries.java:59: error: no viable alternative at input '*fromPerson'"));

        assertTrue(errors.contains("BadQueries.java:62: error: Could not resolve treat target type 'Employe'"));

        assertPanacheErrors(errors, "PanacheBadPerson", 22);
        assertPanacheErrors(errors, "PanacheBadPersonRepository", 10);
    }

    private void assertPanacheErrors(String errors, String name, int start) {
//        assertTrue(errors.contains(name+".java:"+(start)+": warning: missing is not defined (add it to whitelist)"));
        assertTrue(errors.contains(name+".java:"+(start+1)+": warning: Parameter ?2 is not set"));
        assertTrue(errors.contains(name+".java:"+(start+2)+": warning: Parameter :id is not set"));
        assertTrue(errors.contains(name+".java:"+(start+3)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+4)+": warning: Parameter :bar is not set"));
        assertTrue(errors.contains(name+".java:"+(start+5)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+6)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+7)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+8)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+9)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+10)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+11)+": warning: Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java:"+(start+12)+": warning: Too many arguments for 'name'"));
    }

    @Test
    public void testECJ() throws Exception {
        String errors = compileWithECJ("test", "test.test");

        assertECJ(errors);
    }

    private void assertECJ(String errors) {
        assertFalse(errors.contains("GoodQueries.java"));
        assertFalse(errors.contains("PanachePerson.java"));
        assertFalse(errors.contains("PanachePersonRepository.java"));

        assertTrue(errors.contains("mismatched input 'do'") && errors.contains("BadQueries.java (at line 9)"));
        assertTrue(errors.contains("no viable alternative at input 'from") && errors.contains("BadQueries.java (at line 10)"));
        assertTrue(errors.contains("no viable alternative at input '") && errors.contains("BadQueries.java (at line 11)"));
        assertTrue(errors.contains("no viable alternative at input '") && errors.contains("BadQueries.java (at line 12)"));
        assertTrue(errors.contains("Could not interpret path expression 'from") && errors.contains("BadQueries.java (at line 13)"));

        assertTrue(errors.contains("Could not resolve class 'test.Nil' named for instantiation"));
//        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person)"));
//        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person, string)"));
//        assertTrue(errors.contains("test.Pair has no suitable constructor for types (string, string)"));
//        assertTrue(errors.contains("test.Pair has no suitable constructor for types (integer, integer)"));

        assertTrue(errors.contains("Could not resolve root entity 'People'") && errors.contains("BadQueries.java (at line 19)"));
        assertTrue(errors.contains("Could not resolve attribute 'firstName' of 'Person'") && errors.contains("BadQueries.java (at line 20)"));
        assertTrue(errors.contains("Could not resolve attribute 'addr' of 'Person'") && errors.contains("BadQueries.java (at line 21)"));
        assertTrue(errors.contains("Could not resolve attribute 'town' of 'Address'") && errors.contains("BadQueries.java (at line 22)"));
        assertTrue(errors.contains("Could not resolve attribute 'name' of 'Address'") && errors.contains("BadQueries.java (at line 13)"));
        assertTrue(errors.contains("Could not resolve attribute 'type' of 'Country'") && errors.contains("BadQueries.java (at line 24)"));

        assertTrue(errors.contains("Could not interpret attribute 'length' of basic-valued path") && errors.contains("BadQueries.java (at line 26)"));
        assertTrue(errors.contains("Terminal path has no attribute 'length'") && errors.contains("BadQueries.java (at line 27)"));

        assertTrue(errors.contains("Could not interpret path expression 'xxx'") && errors.contains("BadQueries.java (at line 29)"));
//        assertTrue(errors.contains("func is not defined") && errors.contains("BadQueries.java (at line 30)"));
//        assertTrue(errors.contains("custom is not defined") && errors.contains("BadQueries.java (at line 31)"));
//        assertTrue(errors.contains("p is not defined") && errors.contains("BadQueries.java (at line 32)"));
        assertTrue(errors.contains("Could not interpret path expression 'p.name'") && errors.contains("BadQueries.java (at line 32)"));

        assertTrue(errors.contains("mismatched input '.', expecting") && errors.contains("BadQueries.java (at line 39)"));

        assertTrue(errors.contains("Unlabeled ordinal parameter ('?' rather than ?1)") && errors.contains("BadQueries.java (at line 41)"));

        assertTrue(errors.contains("Could not resolve attribute 'x' of 'Person'") && errors.contains("Person.java (at line 22)"));

        assertTrue(errors.contains("Parameter ?2 is not set") && errors.contains("BadQueries.java (at line 46)"));
        assertTrue(errors.contains("Parameter :name is not set") && errors.contains("BadQueries.java (at line 48)"));

        assertTrue(errors.contains("Parameter :hello does not occur in the query") && errors.contains("BadQueries.java (at line 51)"));

        assertTrue(errors.contains("BadQueries.java (at line 46)") && errors.contains("Parameter ?2 is not set"));
        assertTrue(errors.contains("BadQueries.java (at line 48)") && errors.contains("Parameter :name is not set"));

        assertTrue(errors.contains("BadQueries.java (at line 51)") && errors.contains(":hello does not occur in the query"));

        assertTrue(errors.contains("no viable alternative at input '") && errors.contains("token recognition error at: ''gavin'")
                && errors.contains("BadQueries.java (at line 56)"));
        assertTrue(errors.contains("no viable alternative at input '") && errors.contains("token recognition error at: ''gavin'")
                && errors.contains("BadQueries.java (at line 57)"));

        assertTrue(errors.contains("no viable alternative at input '*fromPerson'") && errors.contains("BadQueries.java (at line 59)"));

        assertTrue(errors.contains("Could not resolve treat target type 'Employe'") && errors.contains("BadQueries.java (at line 62)"));

        assertPanacheErrorsEcj(errors, "PanacheBadPerson", 22);
        assertPanacheErrorsEcj(errors, "PanacheBadPersonRepository", 10);
    }

    private void assertPanacheErrorsEcj(String errors, String name, int start) {
//        assertTrue(errors.contains(name+".java (at line "+(start)+")") && errors.contains("Could not interpret path expression 'missing'"));
        assertTrue(errors.contains(name+".java (at line "+(start+1)+")") && errors.contains("Parameter ?2 is not set"));
        assertTrue(errors.contains(name+".java (at line "+(start+2)+")") && errors.contains("Parameter :id is not set"));
        assertTrue(errors.contains(name+".java (at line "+(start+3)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+4)+")") && errors.contains("Parameter :bar is not set"));
        assertTrue(errors.contains(name+".java (at line "+(start+5)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+6)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+7)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+8)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+9)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+10)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+11)+")") && errors.contains("Missing required argument for 'name'"));
        assertTrue(errors.contains(name+".java (at line "+(start+12)+")") && errors.contains("Too many arguments for 'name'"));
    }

    @Test
    public void testEclipse() throws Exception {
        forceEclipseForTesting = true;
        String errors = compileWithECJ("test", "test.test");
        
        assertECJ(errors);

        forceEclipseForTesting = false;
    }

    private String compileWithJavac(String... packages) throws IOException {
        Path tempDir = Files.createTempDirectory("validator-test-out");

        List<String> files = new ArrayList<>();

//        files.add("-verbose");

        files.add("-d");
        files.add(tempDir.toString());

        files.add("-classpath");
        StringBuilder cp = new StringBuilder();

        if (System.getProperty("gradle")!=null) {
            cp.append("build/libs/query-validator-2.0-SNAPSHOT.jar");
            cp.append(":build/classes/java/main:build/classes/groovy/main");
        }
        else {
            cp.append("out/production/query-validator");
            cp.append(":build/classes/java/main");
        }

        Files.list(TEST_LIBS)
                .map(Path::toString)
                .filter(s -> !s.contains("/ecj-") && ! s.contains("/org.eclipse.jdt.core_"))
                .forEach(s -> cp.append(":").append(s));

        System.out.println(cp);
        files.add(cp.toString());

        for (String pack: packages) {
            Files.list(Paths.get("src/test/source")
                        .resolve(pack.replace('.', '/')))
                    .map(Path::toString)
                    .filter(s -> s.endsWith(".java"))
                    .forEach(files::add);
        }

        String[] args = files.toArray(new String[0]);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        getSystemJavaCompiler().run(null, System.out, err, args);

        String errors = err.toString();
        System.out.println(errors);
        return errors;
    }

    private String compileWithECJ(String... packages) throws IOException {
        Path tempDir = Files.createTempDirectory("validator-test-out");

        List<String> files = new ArrayList<>();

//        files.add("-verbose");

        files.add("-1.8");

        files.add("-d");
        files.add(tempDir.toString());

        files.add("-classpath");
        StringBuilder cp = new StringBuilder();

        boolean useFatjar;
        if (System.getProperty("gradle")!=null) {
            useFatjar = forceEclipseForTesting
                    && Files.exists(Paths.get("build/libs/query-validator-2.0-SNAPSHOT-all.jar"));
            if (useFatjar) {
                cp.append("build/libs/query-validator-2.0-SNAPSHOT-all.jar");
            }
            else {
                cp.append("build/libs/query-validator-2.0-SNAPSHOT.jar");
                cp.append(":build/classes/java/main:build/classes/groovy/main");
            }
        }
        else {
            useFatjar = false;
            cp.append("build/classes/java/main:build/classes/groovy/main");
        }

        Files.list(TEST_LIBS)
                .map(Path::toString)
                .filter(s -> useFatjar ?
                        s.contains("/javax.persistence")
                            || s.contains("/quarkus-")
                            || s.contains("/hibernate-core")
                            || s.contains("/org.eclipse.jdt.core_") :
                        !s.contains(forceEclipseForTesting ?
                            "/ecj-" : "/org.eclipse.jdt.core_"))

                .forEach(s -> cp.append(":").append(s));

        System.out.println(cp);
        files.add(cp.toString());

        for (String pack: packages) {
            Files.list(Paths.get("src/test/source")
                        .resolve(pack.replace('.', '/')))
                    .map(Path::toString)
                    .filter(s -> s.endsWith(".java"))
                    .forEach(files::add);
        }

        String[] args = files.toArray(new String[0]);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        BatchCompiler.compile(args,
                new PrintWriter(System.out),
                new PrintWriter(err), null);

        String errors = err.toString();
        System.out.println(errors);
        return errors;
    }
}