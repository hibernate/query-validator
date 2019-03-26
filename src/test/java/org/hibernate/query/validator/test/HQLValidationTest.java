package org.hibernate.query.validator.test;

import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HQLValidationTest {

    @Test
    public void run() throws Exception {
        String errors = compile("test");

        assertFalse(errors.contains("GoodQueries.java:"));

        assertTrue(errors.contains("BadQueries.java:6: error: unexpected token: do"));
        assertTrue(errors.contains("BadQueries.java:7: error: unexpected token"));
        assertTrue(errors.contains("BadQueries.java:8: error: unexpected token: select"));
        assertTrue(errors.contains("BadQueries.java:9: error: unexpected token: ="));
        assertTrue(errors.contains("BadQueries.java:10: error: unexpected token: from"));
        assertTrue(errors.contains("BadQueries.java:10: error: missing from clause or select list"));

        assertTrue(errors.contains("BadQueries.java:12: error: test.Nil does not exist"));
        assertTrue(errors.contains("BadQueries.java:13: error: test.Pair has no suitable constructor"));
        assertTrue(errors.contains("BadQueries.java:14: error: test.Pair has no suitable constructor"));

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

        assertTrue(errors.contains("Person.java:21: error: Person has no mapped x"));

    }

    private String compile(String pack) throws IOException {
        Path tempDir = Files.createTempDirectory("validator-test-out");

        List<String> files = new ArrayList<>();

//        files.add("-verbose");

        files.add("-d");
        files.add(tempDir.toString());

        files.add("-classpath");
        StringBuilder cp = new StringBuilder();
//        cp.append("target/query-validator-1.0-SNAPSHOT.jar");
        cp.append(":target/classes");
        Files.list(Paths.get("lib"))
                .map(Path::toString)
                .filter(s->s.endsWith(".jar")&&!s.endsWith("-sources.jar"))
                .forEach(s->cp.append(":").append(s));
        files.add(cp.toString());

        Files.list(Paths.get("src/test/source/" + pack))
                .map(Path::toString)
                .filter(s->s.endsWith(".java"))
                .forEach(files::add);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, err, files.toArray(new String[0]));
        assert rc!=0;
        String errors = err.toString();
        System.out.println(errors);
        return errors;
    }

}