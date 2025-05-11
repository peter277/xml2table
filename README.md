# xml2table

Simple XML to flat file (e.g. CSV, TSV) conversion utility, modified and extended from [xml2csv](https://github.com/fordfrog/xml2csv) project.

## What it does exactly?

It converts any XML file to flat file format:

    <root>
        <item>
        	<subitem1>
	            <value1>...</value1>
	            <value2>...</value2>
        	</subitem1>
        	<subitem2>
	            <value3>...</value3>
        	</subitem2>
        </item>
        <item>
            <subitem1>
	            <value1>...</value1>
        	</subitem1>
        	<subitem2>
	            <value3>...</value3>
        	</subitem2>
        </item>
        ...
    </root>

Choose any XML element using XPath expression in order to select XML elements
for conversion to flat file. Only child elements that match the expression will
be converted.

## Prerequisities

* JRE or JDK 11+
* Apache Maven 3+ (to compile the application yourself)

## Compilation

Run `mvn package` in the root directory of the sources, where pom.xml file is
located.

## Running

Here is the usage information that xml2table outputs if run without parameters:

    Usage: xml2table [-hV] ([--parallel[=<threads>]] (--input-file=<file>...
                     [--input-file=<file>...]... | --input-dir=<dir>)
                     (--output-file=<file> | --output-dir=<dir>))
                     (--row-item-name=<XPath> --columns=<child XPath>[,<child
                     XPath>...] [--columns=<child XPath>[,<child XPath>...]]...
                     [--separator=<string>] [--no-quote] [--no-header] [--trim]
                     [--join-values] [--join-separator=<string>])
                     [--filter-column=<name> --filter-values=<file>
                     [--filter-exclude]]... [--remap-column=<name>
                     --remap-map=<file>]...

    Convert XML to flat files. The application reads and writes files using UTF-8
    encoding.

      -h, --help                 Show this help message and exit.
      -V, --version              Print version information and exit.

    File processing options:

      --parallel[=<threads>] Enable parallel execution.
                             Optionally specify number of threads to run in
                               parallel (max: lesser of available processors
                               and number of input files). If used without a
                               value, uses max threads. If omitted, runs
                               single-threaded.
      --input-file=<file>... Path to the input XML file(s).
      --input-dir=<dir>      Path to input directory containing XML files.
                               Mutually exclusive with --input-file option.
      --output-file=<file>   Path to the output file.
      --output-dir=<dir>     Path to output directory. Will be created if it
                               does not exist. Output file name will be the
                               same as input file name with the extension
                               replaced by .txt. Mutually exclusive with
                               --output-file option.

    General options:

      --row-item-name=<XPath>
                             Parent XPath referring to the XML element that
                               will be traversed using child XPath
                               specifications from --columns and converted into
                               a row. It cannot end with a slash (/).
      --columns=<child XPath>[,<child XPath>...]
                             List of columns that should be output to the flat
                               file. Columns are specified as child XPath
                               expressions relative to --row-item-name.
      --separator=<string>   String that should be used to separate output
                               columns.
                               Default: ,
      --no-quote             Do not quote values in flat file output. By
                               default all values are quoted.
      --no-header            Do not output header line with column names. By
                               default header line is output.
      --trim                 Trim leading and trailing whitespace from output
                               values. By default values are not trimmed.
      --join-values          Join multiple values matched by a child XPath for
                               a column into a single string using a separator
                               (default: ||). By default, the first matched
                               value for the column is selected and stored.
      --join-separator=<string>
                             Separator used to join multiple values matched by
                               a child XPath for a column when the
                               --join-values option is enabled.
                               Default: ||

    Filtering options:

      --filter-column=<name> Name of the column to filter on. You can specify
                               multiple filters by using this option group
                               multiple times.
      --filter-values=<file> Path to file containing values that the filter
                               should use. Empty rows are added to the values
                               too.
      --filter-exclude       Invert filter to exclude matching rows instead of
                               the default of including them.

    Remapping (value replacement) options:

      --remap-column=<name>  Name of the column to remap. You can specify
                               multiple remap rules by using this option group
                               multiple times. Remapping is done after
                               filtering.
      --remap-map=<file>     Path to file containing original value and new
                               value pairs. The file uses CSV format. Values
                               can be escaped either using single-quote (') or
                               double-quote ("). Quotes within values can be
                               escaped by either doubling them ("" and '') or
                               backslash-escaping them (\" and \').

## License and Acknowledgements

xml2table is distributed under the MIT License.

This project was initially based on [xml2csv](https://github.com/fordfrog/xml2csv),
created by Miroslav Šulc and released under the MIT License.

## Change Log

### Version 0.1.0

May 2025. Initial release, forked from [xml2csv](https://github.com/fordfrog/xml2csv).
