package com.javatodev.finance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerfileTest {

    @Test
    void finalStageRunsAsNonRootUser() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("Dockerfile")).stream()
                .map(String::trim)
                .toList();
        int finalStage = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toUpperCase(Locale.ROOT).startsWith("FROM ")) {
                finalStage = i;
            }
        }
        assertTrue(finalStage >= 0, "Dockerfile must contain a FROM instruction");

        int userLine = -1;
        int commandLine = -1;
        String user = "";
        for (int i = finalStage + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String instruction = line.toUpperCase(Locale.ROOT);
            if (instruction.startsWith("USER ")) {
                userLine = i;
                user = line.substring(5).trim().split("[\\s:]")[0];
            }
            if (commandLine < 0 && (instruction.startsWith("ENTRYPOINT ") || instruction.startsWith("CMD "))) {
                commandLine = i;
            }
        }

        assertTrue(userLine >= 0, "Final stage must declare a USER");
        assertFalse(user.isEmpty() || user.equalsIgnoreCase("root") || user.matches("0+"),
                "Final stage must use a non-root user");
        assertTrue(commandLine >= 0, "Final stage must declare ENTRYPOINT or CMD");
        assertTrue(userLine < commandLine, "USER must appear before ENTRYPOINT or CMD");
    }
}
