/**
 * Copyright 2012 Miroslav Šulc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.peter277.xml2table;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Main class.
 *
 * @author fordfrog (original author)
 */
@Command(name = "xml2table", version = "0.1.0",
         mixinStandardHelpOptions = true,
         sortOptions = false,
         description = "%nConvert XML to flat files. The application reads and writes files using UTF-8 encoding.%n")
public class Main implements Runnable {
    @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nFile processing options:%n%n")
    private FileProcessingOptions fileProcessingOptions = new FileProcessingOptions();

    @Mixin
    private ConvertorSettings convertorSettings;

    @ArgGroup(exclusive = false, multiplicity = "0..*", heading = "%nFiltering options:%n%n")
    private final List<FilteringOptions> filteringOptions = new ArrayList<>();

    @ArgGroup(exclusive = false, multiplicity = "0..*", heading = "%nRemapping (value replacement) options:%n%n")
    private final List<RemappingOptions> remappingOptions = new ArrayList<>();

    /**
     * Main method.
     *
     * @param args
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Process any filtering options
        for (FilteringOptions filteringOption : filteringOptions) {
            Filter filter = new Filter();
            filter.setColumn(filteringOption.filterColumn);
            filter.setValues(loadValues(filteringOption.filterValuesFile));
            filter.setExclude(filteringOption.filterExclude);
            convertorSettings.filters.addFilter(filter);
        }

        // Process any remapping options
        for (RemappingOptions remappingOption : remappingOptions) {
            Remapping remapping = new Remapping();
            remapping.setColumn(remappingOption.remapColumn);
            remapping.setMap(loadMap(remappingOption.remapMapFile));
            convertorSettings.remappings.addRemapping(remapping);
        }

        // If input directory is specified, add all XML files in the directory
        // to the input files list
        if (fileProcessingOptions.inputOptions.inputDir != null) {
            if (fileProcessingOptions.outputOptions.outputDir == null) {
                System.err.println("Error: Output directory must be specified when input directory is used.");
                return;
            }

            try {
                fileProcessingOptions.inputOptions.inputFiles = Files.list(fileProcessingOptions.inputOptions.inputDir)
                        .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
                        .toArray(Path[]::new);
            } catch (IOException e) {
                System.err.println("Error processing input directory: " + e.toString());
                return;
            }
        }

        // If multiple input files are specified, ensure that an output directory is specified
        if (fileProcessingOptions.inputOptions.inputFiles.length > 1 && fileProcessingOptions.outputOptions.outputFile != null) {
            System.err.println("Error: Output directory must be specified when multiple input files are used.");
            return;
        }

        // Prepare and check output directory if specified
        if (fileProcessingOptions.outputOptions.outputDir != null) {
            try {
                if (!Files.exists(fileProcessingOptions.outputOptions.outputDir)) {
                    Files.createDirectories(fileProcessingOptions.outputOptions.outputDir);
                    System.err.println("Info: Created output directory: " + fileProcessingOptions.outputOptions.outputDir.toString());
                }
            } catch (IOException e) {
                System.err.println("Error creating output directory: " + e.toString());
                return;
            }

            // Check if output directory specified is actually a directory
            if (!Files.isDirectory(fileProcessingOptions.outputOptions.outputDir)) {
                System.err.println("Error: Output directory is not a directory.");
                return;
            }

            // Check if output directory is writable
            if (!Files.isWritable(fileProcessingOptions.outputOptions.outputDir)) {
                System.err.println("Error: Output directory is not writable.");
                return;
            }
        }

        // Determine parallelism
        int parallelism;
        if (fileProcessingOptions.parallelOption == null) {
            parallelism = 1;
        } else if ("__AUTO__".equals(fileProcessingOptions.parallelOption)) {
            parallelism = Math.min(Runtime.getRuntime().availableProcessors(), fileProcessingOptions.inputOptions.inputFiles.length);
        } else {
            int requested = Integer.parseInt(fileProcessingOptions.parallelOption);
            int max = Runtime.getRuntime().availableProcessors();
            parallelism = Math.min(Math.min(requested, max), fileProcessingOptions.inputOptions.inputFiles.length);
        }

        System.err.println("Info: Using " + parallelism + " threads for processing");

        // Initiate file processing using thread pool executor service
        ExecutorService executorService = Executors.newFixedThreadPool(parallelism);
        
        List<Future<String>> futures = new ArrayList<>();
        int successCount = 0;
        Map<Path, Exception> failedFiles = new HashMap<>();

        for (Path file : fileProcessingOptions.inputOptions.inputFiles) {
            try {
                System.err.println("Submitting file for processing: " + file.toString());

                // Create a callable task for each file
                Callable<String> task = () -> {
                    if (fileProcessingOptions.outputOptions.outputDir != null) {
                        Path outputFilePath = fileProcessingOptions.outputOptions.outputDir.resolve(file.getFileName().toString().replaceAll("(?i)\\.xml$", ".txt"));
                        Convertor.newConvertorFromFiles(file, outputFilePath, convertorSettings).convert();
                    }
                    else {
                        Convertor.newConvertorFromFiles(file, fileProcessingOptions.outputOptions.outputFile, convertorSettings).convert();
                    }

                    return file.toString();
                };

                futures.add(executorService.submit(task));
            }
            catch (Exception e) {
                System.err.println("Error: " + e.getClass().getSimpleName() + ": " + e.toString());
            }
        }

        executorService.shutdown();

        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
            System.err.println("Error: " + e.toString());
        }

        // Check for exceptions in the futures
        for (int i = 0; i < fileProcessingOptions.inputOptions.inputFiles.length; i++) {
            Path file = fileProcessingOptions.inputOptions.inputFiles[i];
            Future<String> future = futures.get(i);

            try {
                String result = future.get();
                System.err.println("Successfully processed file: " + result);
                successCount++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                failedFiles.put(file, cause instanceof Exception ? (Exception) cause : new Exception(cause));
            } catch (InterruptedException e) {
                System.err.println("Error: " + e.toString());
            }
        }

        // Print summary
        System.err.println("\nProcessing Summary:\n-------------------");
        System.err.println("Total files: " + fileProcessingOptions.inputOptions.inputFiles.length);
        System.err.println("Successful: " + successCount);
        System.err.println("Failed: " + failedFiles.size());
        for (Map.Entry<Path, Exception> entry : failedFiles.entrySet()) {
            System.out.println("File: " + entry.getKey().toString() + " | Error: " + entry.getValue().getClass().getSimpleName() + ": " + entry.getValue().getMessage());
        }
    }

    private static class FileProcessingOptions {
        @ArgGroup(exclusive = true, multiplicity = "1")
        InputOptions inputOptions = new InputOptions();

        @ArgGroup(exclusive = true, multiplicity = "1")
        OutputOptions outputOptions = new OutputOptions();

        @Option(names = "--parallel", 
        description = {
            "Enable parallel execution.",
            "Optionally specify number of threads to run in parallel (max: lesser of available processors and number of input files). " +
            "If used without a value, uses max threads. " +
            "If omitted, runs single-threaded."
            },
        paramLabel = "<threads>",
        arity = "0..1", 
        fallbackValue = "__AUTO__")
        String parallelOption;

        static class InputOptions {
            @Option(names = {"--input-file"}, arity = "1..*", paramLabel = "<file>", description = "Path to the input XML file(s).")
            Path inputFiles[] = null;

            @Option(names = {"--input-dir"}, arity = "1", paramLabel = "<dir>", description = "Path to input directory containing XML files. Mutually exclusive with --input-file option.")
            Path inputDir = null;
        }

        static class OutputOptions {
            @Option(names = {"--output-file"}, paramLabel = "<file>", description = "Path to the output file.")
            Path outputFile = null;

            @Option(names = {"--output-dir"}, paramLabel = "<dir>", description = "Path to output directory. Will be created if it does not exist. Output file name will be the same as input file name with the extension replaced by .txt. Mutually exclusive with --output-file option.")
            Path outputDir = null;
        }
    }

    private static class FilteringOptions {
        @Option(names = {"--filter-column"}, required = true, paramLabel = "<name>", description = "Name of the column to filter on. You can specify multiple filters by using this option group multiple times.")
        String filterColumn = null;

        @Option(names = {"--filter-values"}, required = true, paramLabel = "<file>", description = "Path to file containing values that the filter should use. Empty rows are added to the values too.")
        Path filterValuesFile = null;

        @Option(names = {"--filter-exclude"}, description = "Invert filter to exclude matching rows instead of the default of including them.")
        boolean filterExclude = false;
    }

    private static class RemappingOptions {
        @Option(names = {"--remap-column"}, required = true, paramLabel = "<name>", description = "Name of the column to remap. You can specify multiple remap rules by using this option group multiple times. Remapping is done after filtering.")
        String remapColumn = null;

        @Option(names = {"--remap-map"}, required = true, paramLabel = "<file>",
            description = {"Path to file containing original value and new value pairs. The file uses CSV format. "+
                           "Values can be escaped either using single-quote (') or double-quote (\"). "+
                           "Quotes within values can be escaped by either doubling them (\"\" and '') or backslash-escaping them (\\\" and \\')."}
        )
        Path remapMapFile = null;
    }

    /**
     * Loads list of values from specified file.
     *
     * @param file file path
     *
     * @return collection of loaded values
     */
    private static Collection<String> loadValues(final Path file) {
        @SuppressWarnings("CollectionWithoutInitialCapacity")
        final Collection<String> values = new HashSet<>();

        try (final BufferedReader reader = Files.newBufferedReader(
                file, Charset.forName("UTF-8"))) {
            String line = reader.readLine();

            while (line != null) {
                values.add(line);
                line = reader.readLine();
            }
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to load values", ex);
        }

        return values;
    }

    /**
     * Loads key value pairs from specified file. Values must be separated with
     * comma.
     *
     * @param file file path
     *
     * @return map of loaded key value pairs
     */
    private static Map<String, String> loadMap(final Path file) {
        @SuppressWarnings("CollectionWithoutInitialCapacity")
        final Map<String, String> map = new HashMap<>();

        try (final BufferedReader reader = Files.newBufferedReader(
                file, Charset.forName("UTF-8"))) {
            String line = reader.readLine();

            while (line != null) {
                if (!line.isEmpty()) {
                    final String[] pair = CsvUtils.parseValues(line);
                    map.put(pair[0], pair.length > 1 ? pair[1] : "");
                }

                line = reader.readLine();
            }
        } catch (final IOException ex) {
            throw new RuntimeException("Failed to load map", ex);
        }

        return map;
    }
}
