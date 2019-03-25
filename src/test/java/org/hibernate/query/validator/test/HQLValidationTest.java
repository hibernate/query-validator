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
        System.out.println(errors);

        assertFalse(errors.contains("Person.java:19:"));

        assertFalse(errors.contains("Queries.java:6:"));
        assertFalse(errors.contains("Queries.java:7:"));
        assertFalse(errors.contains("Queries.java:8:"));
        assertFalse(errors.contains("Queries.java:9:"));
        assertFalse(errors.contains("Queries.java:10:"));

        assertFalse(errors.contains("Queries.java:23:"));
        assertFalse(errors.contains("Queries.java:24:"));

        assertFalse(errors.contains("Queries.java:27:"));
        assertFalse(errors.contains("Queries.java:28:"));

        assertFalse(errors.contains("Queries.java:36:"));
        assertFalse(errors.contains("Queries.java:37:"));

        assertFalse(errors.contains("Queries.java:39:"));
        assertFalse(errors.contains("Queries.java:40:"));
        assertFalse(errors.contains("Queries.java:41:"));
        assertFalse(errors.contains("Queries.java:42:"));

        assertFalse(errors.contains("Queries.java:44:"));
        assertFalse(errors.contains("Queries.java:45:"));
        assertFalse(errors.contains("Queries.java:46:"));

        assertFalse(errors.contains("Queries.java:48:"));
        assertFalse(errors.contains("Queries.java:49:"));
        assertFalse(errors.contains("Queries.java:50:"));
        assertFalse(errors.contains("Queries.java:51:"));
        assertFalse(errors.contains("Queries.java:52:"));
        assertFalse(errors.contains("Queries.java:53:"));
        assertFalse(errors.contains("Queries.java:54:"));
        assertFalse(errors.contains("Queries.java:55:"));

        assertFalse(errors.contains("Queries.java:61:"));

        assertFalse(errors.contains("Queries.java:63:"));
        assertFalse(errors.contains("Queries.java:64:"));
        assertFalse(errors.contains("Queries.java:65:"));
        assertFalse(errors.contains("Queries.java:66:"));
        assertFalse(errors.contains("Queries.java:67:"));

        assertFalse(errors.contains("Queries.java:71:"));
        assertFalse(errors.contains("Queries.java:73:"));
        assertFalse(errors.contains("Queries.java:74:"));
        assertFalse(errors.contains("Queries.java:75:"));

        assertFalse(errors.contains("Queries.java:77:"));

        assertFalse(errors.contains("Queries.java:80:"));
        assertFalse(errors.contains("Queries.java:81:"));

        assertFalse(errors.contains("Queries.java:83:"));
        assertFalse(errors.contains("Queries.java:84:"));
        assertFalse(errors.contains("Queries.java:85:"));
        assertFalse(errors.contains("Queries.java:86:"));
        assertFalse(errors.contains("Queries.java:87:"));
        assertFalse(errors.contains("Queries.java:88:"));

        assertFalse(errors.contains("Queries.java:90:"));
        assertFalse(errors.contains("Queries.java:91:"));
        assertFalse(errors.contains("Queries.java:92:"));

        assertFalse(errors.contains("Queries.java:94:"));
        assertFalse(errors.contains("Queries.java:95:"));
        assertFalse(errors.contains("Queries.java:96:"));

        assertFalse(errors.contains("Queries.java:98:"));
        assertFalse(errors.contains("Queries.java:99:"));
        assertFalse(errors.contains("Queries.java:100:"));

        assertTrue(errors.contains("Person.java:20: error: Person has no mapped x"));
        assertTrue(errors.contains("Queries.java:12: error: unexpected token: do"));
        assertTrue(errors.contains("Queries.java:13: error: unexpected token"));
        assertTrue(errors.contains("Queries.java:14: error: unexpected token: select"));
        assertTrue(errors.contains("Queries.java:15: error: unexpected token: ="));
        assertTrue(errors.contains("Queries.java:16: error: unexpected token: from"));
        assertTrue(errors.contains("Queries.java:16: error: FROM expected"));
        assertTrue(errors.contains("Queries.java:18: error: People is not mapped"));
        assertTrue(errors.contains("Queries.java:19: error: Person has no mapped firstName"));
        assertTrue(errors.contains("Queries.java:20: error: Person has no mapped addr"));
        assertTrue(errors.contains("Queries.java:21: error: Address has no mapped town"));
        assertTrue(errors.contains("Queries.java:25: error: Address has no mapped name"));
        assertTrue(errors.contains("Queries.java:29: error: test.Nil does not exist"));
        assertTrue(errors.contains("Queries.java:30: error: test.Pair has no suitable constructor"));
        assertTrue(errors.contains("Queries.java:31: error: test.Pair has no suitable constructor"));
        assertTrue(errors.contains("Queries.java:57: error: entry(*) expression cannot be further de-referenced"));
        assertTrue(errors.contains("Queries.java:59: error: No data type for node:"));
        assertTrue(errors.contains("Queries.java:72: error: string has no mapped length"));
        assertTrue(errors.contains("Queries.java:78: error: Address has no mapped country.type"));
    }
}