package com.github.peter277.xml2table;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Visibility;

public class ConvertorSettings {
    @ArgGroup(exclusive = false, multiplicity = "1", heading = "%nGeneral options:%n%n")
    final GeneralOptions generalOptions = new GeneralOptions();

    final Filters filters = new Filters();
    final Remappings remappings = new Remappings();

    static class GeneralOptions {
        @Option(names = {"--row-item-name"}, required = true, paramLabel = "<XPath>", description = "Parent XPath referring to the XML element that will be traversed using child XPath specifications from --columns and converted into a row. It cannot end with a slash (/).")
        String itemName = null;

        @Option(names = {"--columns"}, split = ",", required = true, paramLabel = "<child XPath>", description = "List of columns that should be output to the flat file. Columns are specified as child XPath expressions relative to --row-item-name.")
        String[] columns = null;

        @Option(names = {"--separator"}, paramLabel = "<string>", description = "String that should be used to separate output columns.", defaultValue = ",", showDefaultValue = Visibility.ALWAYS)
        String separator = ",";

        @Option(names = {"--no-quote"}, description = "Do not quote values in flat file output. By default all values are quoted.")
        boolean noQuote = false;

        @Option(names = {"--no-header"}, description = "Do not output header line with column names. By default header line is output.")
        boolean noHeader = false;

        @Option(names = {"--trim"}, description = "Trim leading and trailing whitespace from output values. By default values are not trimmed.")
        boolean trimValues = false;

        @Option(names = {"--join-values"}, description = "Join multiple values matched by a child XPath for a column into a single string using a separator (default: ||). By default, the first matched value for the column is selected and stored.")
        boolean join = false;

        @Option(names = {"--join-separator"}, paramLabel = "<string>", description = "Separator used to join multiple values matched by a child XPath for a column when the --join-values option is enabled.", defaultValue = "||", showDefaultValue = Visibility.ALWAYS)
        String joinSeparator = "||";
    }
}
