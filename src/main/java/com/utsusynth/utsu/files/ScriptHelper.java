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
    private final boolean isWindows;

    public ScriptHelper(final ExternalProcessRunner runner) {
        this.runner = runner;

        final String os = System.getProperty("os.name").toLowerCase();
        isWindows = os.contains("win");
    }

    public static String getScriptLine(final String[] args) {
        return "\"" + String.join("\" \"", args) + "\"";
    }

    public void RunScriptParallel(final ArrayList<String> scriptLines) throws IOException {

        // Get non-empty lines
        final List<String> lines = scriptLines.stream()
                .filter(l -> l.length() > 0)
                .distinct()
                .collect(Collectors.toList());

        // We may have nothing to do
        if (lines.isEmpty()) return;

        // This may be the optimum size
        final int optimalThreads = Runtime.getRuntime().availableProcessors() + 1;

        // Decide how to split the original script lines
        final int scriptLength = lines.size();
        final int maxThreads = scriptLength > minLinesPerScript ? divideFloor(scriptLength, minLinesPerScript) : 1;
        final int threadPoolSize = optimalThreads > maxThreads ? maxThreads : optimalThreads;

        if (threadPoolSize == 1) {
            // Avoid the overhead
            RunScriptSerial(scriptLines);
            return;
        }

        final AtomicInteger counter = new AtomicInteger(0);
        final Collection<List<String>> smallerScriptLines = lines.stream()
                        .collect(Collectors.groupingBy(it -> counter.getAndIncrement() % threadPoolSize))
                        .values();

        // Set up a thread pool for asynchronous rendering.
        final ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        final ArrayList<Callable<Void>> tasks = new ArrayList<>();

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

    public void RunScriptSerial(final ArrayList<String> scriptLines) throws IOException {

        // convert this to a single string, suitable for running
        final String script = scriptLines.stream().filter(l -> l.length() > 0).collect(Collectors.joining(System.lineSeparator()));
        if (script.length() == 0) return;

        // Take the generated script, write it to the FS, then run it
        final File scriptFile = getTempScriptFile();
        FileUtils.writeStringToFile(scriptFile, script, StandardCharsets.UTF_8);
        runner.runProcess(scriptFile.getAbsolutePath());
    }

    private File getTempScriptFile() throws IOException {

        // Create a file to hold our wavtool script
        final String scriptExtension = isWindows ? ".cmd" : ".sh";
        final File scriptFile = File.createTempFile("utsu-", scriptExtension);
        scriptFile.deleteOnExit();

        return scriptFile;
    }
}