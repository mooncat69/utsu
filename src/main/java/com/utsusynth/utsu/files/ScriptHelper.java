package com.utsusynth.utsu.files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.utsusynth.utsu.engine.ExternalProcessRunner;

import org.apache.commons.io.FileUtils;

public class ScriptHelper {

    private static final int minLinesPerScript = 10;
    private final ExternalProcessRunner runner;
    private final boolean canExecuteScripts = canExecuteScriptFiles();
    private static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public ScriptHelper(final ExternalProcessRunner runner) {
        this.runner = runner;
    }

    public static String getScriptLine(final String[] args) {
        return "\"" + String.join("\" \"", args) + "\"";
    }

    public void runScriptParallel(final ArrayList<String[]> scriptLines) throws IOException {

        // Get non-empty lines
        final List<String[]> lines = scriptLines.stream()
                .filter(l -> l.length > 0) // Cached scripts will have no parameters
                .distinct()
                .collect(Collectors.toList());

        // We may have nothing to do
        if (lines.isEmpty()) return;

        // This may be the optimum size
        final int optimalThreads = Runtime.getRuntime().availableProcessors() + 1;

        // Decide how to split the original script lines
        final int scriptLength = lines.size();
        final int maxThreads = scriptLength > minLinesPerScript ? divideFloor(scriptLength, minLinesPerScript) : 1;
        final int threadPoolSize = canExecuteScripts && optimalThreads > maxThreads ? maxThreads : optimalThreads;

        if (threadPoolSize == 1) {
            // Avoid the overhead
            runScriptSerial(scriptLines);
            return;
        }

        // Set up a thread pool for asynchronous rendering.
        final ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        final ArrayList<Callable<Void>> tasks = new ArrayList<>();        

        if (canExecuteScripts) {

            final AtomicInteger counter = new AtomicInteger(0);
            final Collection<List<String>> smallerScriptLines = lines.stream()
                            .filter(l -> l.length > 0) // Cached scripts will have no parameters
                            .map(l -> getScriptLine(l))
                            .filter(l -> !l.isBlank())
                            .collect(Collectors.groupingBy(it -> counter.getAndIncrement() % threadPoolSize))
                            .values();

            smallerScriptLines.forEach(s -> {
            
                final String smallerScript = String.join(System.lineSeparator(), s);

                try {
                    final File scriptFile = getTempScriptFile();
                    FileUtils.writeStringToFile(scriptFile, smallerScript, StandardCharsets.UTF_8);

                    tasks.add(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            runner.runProcess(scriptFile.getAbsolutePath());
                            return null;
                        }
                    });
                } catch (final IOException e) {
                    // What ??
                }
            });
        } else {
            for (String[] line : lines) {
                tasks.add(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        runner.getProcessOutput(line);
                        return null;
                    }
                });
            }
        }

        try {
            // Now run these scripts
            executor.invokeAll(tasks);
        } catch (final InterruptedException e) {
            // What ??
        } finally {
            try {
                executor.shutdown(); // Shut down thread pool
            } catch (final Exception e) {
                // What??
            }
        }
    }

    private int divideFloor(final int val1, final int val2) {
        return (int)Math.floor((float)val1 / (float)val2);
    }

    public void runScriptSerial(final ArrayList<String[]> scriptLines) throws IOException {

        if (canExecuteScripts) {
            // Take the generated script, write it to the FS, then run it
            final String script = scriptLines.stream()
                .filter(l -> l.length > 0) // Cached scripts will have no parameters
                .map(l -> getScriptLine(l))
                .filter(l -> !l.isBlank())
                .collect(Collectors.joining(System.lineSeparator()));

            final File scriptFile = getTempScriptFile();
            FileUtils.writeStringToFile(scriptFile, script, StandardCharsets.UTF_8);
            runner.runProcess(scriptFile.getAbsolutePath());
        } else {
            for (String[] args: scriptLines) {
                if (args.length == 0) continue;
                runner.getProcessOutput(args);
            }
        }
    }

    private static File getTempScriptFile() throws IOException {

        // Create a file to hold our wavtool script
        final String scriptExtension = isWindows ? ".cmd" : ".sh";
        final File scriptFile = FileHelper.createTempFile("utsu-", scriptExtension);
        scriptFile.deleteOnExit();

        return scriptFile;
    }

    private static boolean canExecuteScriptFiles() {
        try {
            // See if we can write temporary executable scripts to the OS
            final File testScript = getTempScriptFile();
            FileUtils.writeStringToFile(testScript, "echo Hello", StandardCharsets.UTF_8);
            return testScript.canExecute();
        } catch (Exception f) {
            return false;
        }
    }
}