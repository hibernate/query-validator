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

    @Test
    public void testJavac() throws Exception {
        String errors = compileWithJavac("test");

        assertFalse(errors.contains("GoodQueries.java:"));

        assertTrue(errors.contains("BadQueries.java:6: error: unexpected token: do"));
        assertTrue(errors.contains("BadQueries.java:7: error: unexpected token"));
        assertTrue(errors.contains("BadQueries.java:8: error: unexpected token: select"));
        assertTrue(errors.contains("BadQueries.java:9: error: unexpected token: ="));
        assertTrue(errors.contains("BadQueries.java:10: error: unexpected token: from"));
        assertTrue(errors.contains("BadQueries.java:10: error: missing from clause or select list"));

        assertTrue(errors.contains("BadQueries.java:12: error: test.Nil does not exist"));
        assertTrue(errors.contains("BadQueries.java:13: error: test.Pair has no suitable constructor for types (Person)"));
        assertTrue(errors.contains("BadQueries.java:14: error: test.Pair has no suitable constructor for types (Person, string)"));
        assertTrue(errors.contains("BadQueries.java:50: error: test.Pair has no suitable constructor for types (integer, integer)"));
        assertTrue(errors.contains("BadQueries.java:51: error: test.Pair has no suitable constructor for types (string, string)"));

        assertTrue(errors.contains("BadQueries.java:16: error: People is not mapped"));
        assertTrue(errors.contains("BadQueries.java:17: error: Person has no mapped firstName"));
        assertTrue(errors.contains("BadQueries.java:18: error: Person has no mapped addr"));
        assertTrue(errors.contains("BadQueries.java:19: error: Address has no mapped town"));
        assertTrue(errors.contains("BadQueries.java:20: error: Address has no mapped name"));
        assertTrue(errors.contains("BadQueries.java:21: error: Address has no mapped country.type"));

        assertTrue(errors.contains("BadQueries.java:23: error: ")); //should be: "string has no mapped length"
        assertTrue(errors.contains("BadQueries.java:24: error: string has no mapped length"));

        assertTrue(errors.contains("BadQueries.java:26: warning: xxx is not defined"));
        assertTrue(errors.contains("BadQueries.java:27: warning: func is not defined"));
        assertTrue(errors.contains("BadQueries.java:28: warning: custom is not defined"));
        assertTrue(errors.contains("BadQueries.java:29: warning: p is not defined"));
        assertTrue(errors.contains("BadQueries.java:29: error: p.name is not defined"));

        assertTrue(errors.contains("BadQueries.java:31: error: key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("BadQueries.java:32: error: key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("BadQueries.java:33: error: key(), value(), or entry() argument must be map element"));

        assertTrue(errors.contains("BadQueries.java:36: error: entry() has no members"));

        assertTrue(errors.contains("BadQueries.java:38: error: illegal token: ?"));

        //should be errors:
//        assertTrue(errors.contains("BadQueries.java:40:"));
//        assertTrue(errors.contains("BadQueries.java:41:"));

        assertTrue(errors.contains("Person.java:22: error: Person has no mapped x"));

        assertTrue(errors.contains("BadQueries.java:43: warning: ?2 is not set"));
        assertTrue(errors.contains("BadQueries.java:45: warning: :name is not set"));

        assertTrue(errors.contains("BadQueries.java:48: warning: :hello does not occur in the query"));

    }

    @Test
    public void testECJ() throws Exception {
        String errors = compileWithECJ("test");

        assertFalse(errors.contains("GoodQueries.java"));

        assertTrue(errors.contains("unexpected token: do") && errors.contains("BadQueries.java (at line 6)"));
        assertTrue(errors.contains("unexpected token"));
        assertTrue(errors.contains("unexpected token: select") && errors.contains("BadQueries.java (at line 8)"));
        assertTrue(errors.contains("unexpected token: =") && errors.contains("BadQueries.java (at line 9)"));
        assertTrue(errors.contains("unexpected token: from") && errors.contains("BadQueries.java (at line 10)"));
        assertTrue(errors.contains("missing from clause or select list") && errors.contains("BadQueries.java (at line 10)"));

        assertTrue(errors.contains("test.Nil does not exist"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person, string)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (string, string)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (integer, integer)"));

        assertTrue(errors.contains("People is not mapped") && errors.contains("BadQueries.java (at line 16)"));
        assertTrue(errors.contains("Person has no mapped firstName") && errors.contains("BadQueries.java (at line 17)"));
        assertTrue(errors.contains("Person has no mapped addr") && errors.contains("BadQueries.java (at line 18)"));
        assertTrue(errors.contains("Address has no mapped town") && errors.contains("BadQueries.java (at line 19)"));
        assertTrue(errors.contains("Address has no mapped name") && errors.contains("BadQueries.java (at line 10)"));
        assertTrue(errors.contains("Address has no mapped country.type") && errors.contains("BadQueries.java (at line 21)"));

        assertTrue(errors.contains("") && errors.contains("BadQueries.java (at line 23)")); //should be: "string has no mapped length"
        assertTrue(errors.contains("string has no mapped length") && errors.contains("BadQueries.java (at line 24)"));

        assertTrue(errors.contains("xxx is not defined") && errors.contains("BadQueries.java (at line 26)"));
        assertTrue(errors.contains("func is not defined") && errors.contains("BadQueries.java (at line 27)"));
        assertTrue(errors.contains("custom is not defined") && errors.contains("BadQueries.java (at line 28)"));
        assertTrue(errors.contains("p is not defined") && errors.contains("BadQueries.java (at line 29)"));
        assertTrue(errors.contains("p.name is not defined") && errors.contains("BadQueries.java (at line 29)"));

        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));

        assertTrue(errors.contains("entry() has no members") && errors.contains("BadQueries.java (at line 36)"));

        assertTrue(errors.contains("illegal token: ?") && errors.contains("BadQueries.java (at line 38)"));

        assertTrue(errors.contains("Person has no mapped x") && errors.contains("Person.java (at line 22)"));

        assertTrue(errors.contains("?2 is not set") && errors.contains("BadQueries.java (at line 43)"));
        assertTrue(errors.contains(":name is not set") && errors.contains("BadQueries.java (at line 45)"));

        assertTrue(errors.contains(":hello does not occur in the query") && errors.contains("BadQueries.java (at line 48)"));

    }

    @Test
    public void testEclipse() throws Exception {
        forceEclipseForTesting = true;
        String errors = compileWithECJ("test");

        assertFalse(errors.contains("GoodQueries.java"));

        assertTrue(errors.contains("unexpected token: do") && errors.contains("BadQueries.java (at line 6)"));
        assertTrue(errors.contains("unexpected token"));
        assertTrue(errors.contains("unexpected token: select") && errors.contains("BadQueries.java (at line 8)"));
        assertTrue(errors.contains("unexpected token: =") && errors.contains("BadQueries.java (at line 9)"));
        assertTrue(errors.contains("unexpected token: from") && errors.contains("BadQueries.java (at line 10)"));
        assertTrue(errors.contains("missing from clause or select list") && errors.contains("BadQueries.java (at line 10)"));

        assertTrue(errors.contains("test.Nil does not exist"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (Person, string)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (string, string)"));
        assertTrue(errors.contains("test.Pair has no suitable constructor for types (integer, integer)"));

        assertTrue(errors.contains("People is not mapped") && errors.contains("BadQueries.java (at line 16)"));
        assertTrue(errors.contains("Person has no mapped firstName") && errors.contains("BadQueries.java (at line 17)"));
        assertTrue(errors.contains("Person has no mapped addr") && errors.contains("BadQueries.java (at line 18)"));
        assertTrue(errors.contains("Address has no mapped town") && errors.contains("BadQueries.java (at line 19)"));
        assertTrue(errors.contains("Address has no mapped name") && errors.contains("BadQueries.java (at line 10)"));
        assertTrue(errors.contains("Address has no mapped country.type") && errors.contains("BadQueries.java (at line 21)"));

        assertTrue(errors.contains("") && errors.contains("BadQueries.java (at line 23)")); //should be: "string has no mapped length"
        assertTrue(errors.contains("string has no mapped length") && errors.contains("BadQueries.java (at line 24)"));

        assertTrue(errors.contains("xxx is not defined") && errors.contains("BadQueries.java (at line 26)"));
        assertTrue(errors.contains("func is not defined") && errors.contains("BadQueries.java (at line 27)"));
        assertTrue(errors.contains("custom is not defined") && errors.contains("BadQueries.java (at line 28)"));
        assertTrue(errors.contains("p is not defined") && errors.contains("BadQueries.java (at line 29)"));
        assertTrue(errors.contains("p.name is not defined") && errors.contains("BadQueries.java (at line 29)"));

        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));
        assertTrue(errors.contains("key(), value(), or entry() argument must be map element"));

        assertTrue(errors.contains("entry() has no members") && errors.contains("BadQueries.java (at line 36)"));

        assertTrue(errors.contains("illegal token: ?") && errors.contains("BadQueries.java (at line 38)"));

        assertTrue(errors.contains("Person has no mapped x") && errors.contains("Person.java (at line 22)"));

        assertTrue(errors.contains("?2 is not set") && errors.contains("BadQueries.java (at line 43)"));
        assertTrue(errors.contains(":name is not set") && errors.contains("BadQueries.java (at line 45)"));

        assertTrue(errors.contains(":hello does not occur in the query") && errors.contains("BadQueries.java (at line 48)"));

        forceEclipseForTesting = false;
    }

    private String compileWithJavac(String pack) throws IOException {
        Path tempDir = Files.createTempDirectory("validator-test-out");

        List<String> files = new ArrayList<>();

//        files.add("-verbose");

        files.add("-d");
        files.add(tempDir.toString());

        files.add("-classpath");
        StringBuilder cp = new StringBuilder();
        cp.append("build/libs/query-validator-1.0-SNAPSHOT.jar");
        cp.append(":out/production/query-validator");
        Files.list(Paths.get("lib"))
                .map(Path::toString)
                .filter(s -> s.endsWith(".jar")&&!s.endsWith("-sources.jar"))
                .filter(s -> !s.contains("/ecj-") && ! s.contains("/org.eclipse.jdt.core_"))
                .forEach(s -> cp.append(":").append(s));
        System.out.println(cp);
        files.add(cp.toString());

        Files.list(Paths.get("src/test/source").resolve(pack))
                .map(Path::toString)
                .filter(s->s.endsWith(".java"))
                .forEach(files::add);

        String[] args = files.toArray(new String[0]);
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        getSystemJavaCompiler().run(null, System.out, err, args);

        String errors = err.toString();
        System.out.println(errors);
        return errors;
    }

    private String compileWithECJ(String pack) throws IOException {
        Path tempDir = Files.createTempDirectory("validator-test-out");

        List<String> files = new ArrayList<>();

//        files.add("-verbose");

        files.add("-1.8");

        files.add("-d");
        files.add(tempDir.toString());

        files.add("-classpath");
        StringBuilder cp = new StringBuilder();
        if (forceEclipseForTesting) {
            cp.append("build/libs/query-validator-1.0-SNAPSHOT-all.jar");;
        }
        cp.append("build/libs/query-validator-1.0-SNAPSHOT.jar");
        cp.append(":out/production/query-validator");
        Files.list(Paths.get("lib"))
                .map(Path::toString)
                .filter(s -> s.endsWith(".jar")&&!s.endsWith("-sources.jar"))
                .filter(s -> !s.contains(forceEclipseForTesting ?
                        "/ecj-" : "/org.eclipse.jdt.core_"))
                .forEach(s -> cp.append(":").append(s));
        System.out.println(cp);
        files.add(cp.toString());

        Files.list(Paths.get("src/test/source").resolve(pack))
                .map(Path::toString)
                .filter(s->s.endsWith(".java"))
                .forEach(files::add);

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