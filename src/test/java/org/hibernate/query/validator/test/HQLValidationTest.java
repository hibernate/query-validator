package org.hibernate.query.validator.test;

import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
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

        Files.list(Paths.get("src/test/source/test"))
                .map(Path::toString)
                .filter(s->s.endsWith(".java"))
                .forEach(files::add);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int rc = javac.run(null, null, err, files.toArray(new String[0]));
        assert rc!=0;
        String errors = err.toString();
//        System.out.println(errors);

        assertFalse(errors.contains("Queries.java:6:"));
        assertFalse(errors.contains("Queries.java:7:"));
        assertFalse(errors.contains("Queries.java:8:"));
        assertFalse(errors.contains("Queries.java:9:"));
        assertFalse(errors.contains("Queries.java:10:"));

        assertTrue(errors.contains("Queries.java:12: error: unexpected token: do"));
        assertTrue(errors.contains("Queries.java:13: error: unexpected token"));
        assertTrue(errors.contains("Queries.java:14: error: unexpected token: select"));
        assertTrue(errors.contains("Queries.java:15: error: unexpected token: ="));
        assertTrue(errors.contains("Queries.java:16: error: unexpected token: from"));
        assertTrue(errors.contains("Queries.java:16: error: FROM expected"));
        assertTrue(errors.contains("Queries.java:18: error: People is not mapped"));
        assertTrue(errors.contains("Queries.java:19: error: Property firstName does not exist in class Person"));
        assertTrue(errors.contains("Queries.java:20: error: Property addr does not exist in class Person"));
        assertTrue(errors.contains("Queries.java:21: error: Property"));

    }
}