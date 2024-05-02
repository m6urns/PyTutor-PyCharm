package org.intellij.sdk.PyTutor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.jetbrains.annotations.NotNull;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FunctionManager implements RunManagerListener {
    public static void writeToLibrary(Project project, String functionName, String functionCode) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Path baseDirPath = Path.of(project.getBasePath());
            Path functionFilePath = baseDirPath.resolve(functionName + ".py");
            Path functionManagerFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

            try {
                Files.writeString(functionFilePath, functionCode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                String importStatement = String.format("from %s import %s\n", functionName, functionName);
                Files.writeString(functionManagerFilePath, importStatement, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                System.out.println("Function code written to: " + functionFilePath);
            } catch (IOException e) {
                System.err.println("Error writing to function file: " + e.getMessage());
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    reloadProject(project);
                });
            });
        });
    }

    private static void reloadProject(Project project) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            RefreshQueue.getInstance().refresh(true, true, null, projectDir);
            System.out.println("Project reloaded after writing to function file.");
        } else {
            System.err.println("Failed to find project directory for project: " + project.getName());
        }
    }

    public static void deleteLibraryFiles(Project project) {
        Path baseDirPath = Path.of(project.getBasePath());
        Path functionManagerFilePath = baseDirPath.resolve(PathManager.FUNCTION_MANAGER_FILE_NAME);

        try (var lines = Files.lines(functionManagerFilePath)) {
            lines.map(line -> line.split("\\s+")[1])
                    .map(functionName -> functionName.split("\\.")[0])
                    .distinct()
                    .forEach(functionName -> {
                        Path functionFilePath = baseDirPath.resolve(functionName + ".py");
                        try {
                            Files.deleteIfExists(functionFilePath);
                            System.out.println("Function file deleted: " + functionFilePath);
                        } catch (IOException e) {
                            System.err.println("Error deleting function file: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error reading function manager file: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            Files.deleteIfExists(functionManagerFilePath);
            System.out.println("Function manager file deleted: " + functionManagerFilePath);
        } catch (IOException e) {
            System.err.println("Error deleting function manager file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        PathManager.updatePythonPath(settings);
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        PathManager.updatePythonPath(settings);
    }

    public static void registerProjectListener() {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(com.intellij.openapi.project.ProjectManager.TOPIC, new com.intellij.openapi.project.ProjectManagerListener() {
            @Override
            public void projectClosing(@NotNull Project project) {
                deleteLibraryFiles(project);
            }
        });
    }
}