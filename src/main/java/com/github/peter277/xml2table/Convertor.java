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

import com.ctc.wstx.stax.WstxInputFactory;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * XML to flat file convertor.
 *
 * @author fordfrog (original author)
 */
public class Convertor {
    // IO Resources
    InputStream inputStream = null;
    Writer writer = null;

    XMLInputFactory xMLInputFactory = null;
    XMLStreamReader reader = null;

    // Settings
    String[] columns = null;
    Filters filters = null;
    Remappings remappings = null;
    String separator = ",";
    boolean trim = false;
    boolean join = false;
    String rootItemName = null;
    String joinSeparator = "||";
    boolean quoteValues = true;
    boolean noHeader = false;


    public static Convertor newConvertorFromFiles(final Path infile, final Path outfile, final ConvertorSettings settings)
        throws IOException {        
        Convertor newObj = new Convertor(
            infile, outfile,
            settings.generalOptions.columns, settings.filters, settings.remappings, settings.generalOptions.separator,
            settings.generalOptions.trimValues, settings.generalOptions.join, settings.generalOptions.itemName
        );

        newObj.joinSeparator = settings.generalOptions.joinSeparator;
        newObj.quoteValues = !settings.generalOptions.noQuote;
        newObj.noHeader = settings.generalOptions.noHeader;

        return newObj;
    }

    public static Convertor newConvertorFromStreams(final InputStream inputStream, final Writer writer, final ConvertorSettings settings)
        throws IOException {        
        Convertor newObj = new Convertor(
            inputStream, writer,
            settings.generalOptions.columns, settings.filters, settings.remappings, settings.generalOptions.separator,
            settings.generalOptions.trimValues, settings.generalOptions.join, settings.generalOptions.itemName
        );

        newObj.joinSeparator = settings.generalOptions.joinSeparator;
        newObj.quoteValues = !settings.generalOptions.noQuote;
        newObj.noHeader = settings.generalOptions.noHeader;

        return newObj;
    }

    /**
     * Constructs object to convert input XML file to output flat file (e.g. CSV, TSV). Deprecated - will be refactored in future.
     *
     * @param inputFile  input file path
     * @param outputFile output file path
     * @param columns    array of column names
     * @param filters    optional filters
     * @param remappings optional remappings
     * @param separator  field separator
     * @param trim       whether to trim values or not
     * @param join       whether to join multiple values or not
     * @param itemName   XPath which refers to XML element which will be
     *                   converted to a row
     */
    private Convertor(final Path inputFile, final Path outputFile,
            final String[] columns, final Filters filters,
            final Remappings remappings, final String separator,
            final boolean trim, final boolean join, final String itemName)
            throws IOException {
                this(
                    new BufferedInputStream(Files.newInputStream(inputFile)),
                    Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8),
                    columns, filters, remappings, separator,
                    trim, join, itemName
                );
    }

    /**
     * Constructs object to convert input stream with XML to flat file saved into writer. Deprecated - will be refactored in future.
     *
     * @param inputStream input stream
     * @param writer      writer
     * @param columns     array of column names
     * @param filters     optional filters
     * @param remappings  optional remappings
     * @param separator   field separator
     * @param trim        whether to trim values or not
     * @param join        whether to join multiple values or not
     * @param itemName    XPath which refers to XML element which will be
     *                    converted to a row
     */
    private Convertor(final InputStream inputStream,
            final Writer writer,
            final String[] columns, final Filters filters,
            final Remappings remappings, final String separator,
            final boolean trim, final boolean join, final String itemName)
            throws RuntimeException, IOException
        {
            this.inputStream = inputStream;
            this.writer = writer;
            this.columns = columns;
            this.filters = filters;
            this.remappings = remappings;
            this.separator = separator;
            this.trim = trim;
            this.join = join;
            this.rootItemName = itemName;

            if (itemName.trim().isEmpty()) {
                throw new IllegalArgumentException("itemName is an empty string. ");
            }
    
            if (itemName.trim().length() != 1 && itemName.endsWith("/")) {
                throw new IllegalArgumentException(
                        "itemName cannot end with a shash (/).");
            }

            try {
                this.xMLInputFactory = new WstxInputFactory();
                this.reader = xMLInputFactory.createXMLStreamReader(this.inputStream);
            } catch (final XMLStreamException ex) {
                throw new RuntimeException("XML stream exception: unable to create stream reader", ex);
            }
        }

    /**
     * Converts XML to flat file using settings specified during object construction.
     *
     * @throws RuntimeException Thrown if IO or XML stream exception occurred.
     */
    public void convert() {        
        try {
            if (!this.noHeader) {
                writeHeader();
            }

            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamReader.START_ELEMENT:
                        processRoot(getParentName(null,reader.getLocalName()));
                }
            }

            writer.close();
            reader.close();
        } catch (final IOException ex) {
            throw new RuntimeException("IO operation failed", ex);
        } catch (final XMLStreamException ex) {
            throw new RuntimeException("XML stream exception", ex);
        }
    }

    /**
     * Writes header for delimited flat file.
     *
     * @throws IOException Thrown if problem occurred while writing to output
     *                     file.
     */
    private void writeHeader() throws IOException {
        for (int i = 0; i < this.columns.length; i++) {
            if (i > 0) {
                writer.append(this.separator);
            }

            writer.append(this.quoteValues ? CsvUtils.quoteString(this.columns[i]) : Objects.requireNonNullElse(this.columns[i],""));
        }

        writer.append('\n');

        writer.flush();
    }

    /**
     * Processes root element and its subelements.
     *
     * @param parentElement XPath which refers to parent element
     *
     * @throws XMLStreamException Thrown if problem occurred while reading XML
     *                            stream.
     * @throws IOException        Thrown if IO problem occurred.
     */
    private void processRoot(final String parentElement) throws XMLStreamException,
            IOException {
        final Deque<String> stack = new ArrayDeque<>();
        stack.push(parentElement);

        while (!stack.isEmpty() && reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamReader.START_ELEMENT:
                    final String currentElementPath = getParentName(
                            stack.peek(), reader.getLocalName());

                    if ((currentElementPath).compareTo(rootItemName) == 0) {
                        processItem(currentElementPath);
                    } else {
                        stack.push(currentElementPath);
                    }

                    break;
                case XMLStreamReader.END_ELEMENT:
                    stack.pop();
                    break;
            }
        }
    }

    /**
     * Processes item element.
     *
     * @param parentElement XPath which refers to parent element
     * @param values        values of XML element for current row
     *
     * @throws XMLStreamException Thrown if problem occurred while reading XML
     *                            stream.
     * @throws IOException        Thrown if IO problem occurred.
     */
    private void processItem(final String parentElement)
        throws XMLStreamException, IOException {
        
        final Map<String, List<String>> values = new HashMap<>(columns.length);

        // Process attributes of the root element
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String attributeValue = reader.getAttributeValue(i);
            String attributePath = parentElement + "/@" + attributeName;
            processValue(attributePath.replaceFirst(Pattern.quote(
                rootItemName + "/"), ""), attributeValue, values);
        }

        final Deque<Entry<String, StringBuilder>> stack = new ArrayDeque<>(); // Use Entry as substitute for Pair
        stack.push(new AbstractMap.SimpleEntry<>(parentElement, null));

        while (!stack.isEmpty() && reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamReader.START_ELEMENT:
                    final String currentElementPath = getParentName(
                        stack.peek().getKey(), reader.getLocalName());

                    // Process attributes of the current element
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String attributeName = reader.getAttributeLocalName(i);
                        String attributeValue = reader.getAttributeValue(i);
                        String attributePath = currentElementPath + "/@" + attributeName;
                        processValue(attributePath.replaceFirst(Pattern.quote(
                            rootItemName + "/"), ""), attributeValue, values);
                    }

                    // Process any child elements
                    stack.push(new AbstractMap.SimpleEntry<>(currentElementPath, null));

                    break;
                case XMLStreamReader.CHARACTERS:
                    if (stack.peek().getValue() == null) {
                        stack.peek().setValue(new StringBuilder(100));
                    }
                    stack.peek().getValue().append(reader.getText());

                    break;
                case XMLStreamReader.END_ELEMENT:
                    // If we have reached the closing tag of the root item, concatenate or truncate multiple values (depending on join setting),
                    // apply any filter conditions, and write out the values as a row
                    if ((stack.peek().getKey()).compareTo(rootItemName) == 0) {
                        final Map<String, String> singleValues = new HashMap<>(
                                columns.length);

                        for (Entry<String, List<String>> mapEntry : values.entrySet()) {
                            singleValues.put(mapEntry.getKey(), prepareValue(mapEntry.getValue()));
                        }

                        if (filters == null || filters.matchesFilters(
                                singleValues)) {
                            if (remappings != null) {
                                remappings.replaceValues(singleValues);
                            }

                            writeRow(singleValues);
                        }
                    } else {
                        StringBuilder sb =  stack.peek().getValue();
                        processValue(stack.peek().getKey().replaceFirst(Pattern.quote(
                                rootItemName + "/"), ""), sb == null ? "" : sb.toString(), values);
                    }

                    stack.pop();
                    break;
            }
        }
    }

    /**
     * Writes XML item to flat file as delimited row of columns.
     *
     * @param values    map of values
     *
     * @throws IOException Thrown if problem occurred while writing to output
     *                     file.
     */
    private void writeRow(final Map<String, String> values)
            throws IOException {
        final StringBuilder sb = new StringBuilder(columns.length * 20); // Pre-allocate size

        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(this.separator);
            }

            sb.append(this.quoteValues ? CsvUtils.quoteString(values.get(columns[i])) : Objects.requireNonNullElse(values.get(columns[i]),""));
        }

        writer.append(sb.toString()).append('\n');
        writer.flush();
    }

    /**
     * Joins elements from the list using configured join separator or return first
     * element from the list. Use trim=<code>true</code> to trim values.
     *
     * @param values         list of values
     *
     * @return String containing separated values from the list or first element
     *         from the list.
     */
    private String prepareValue(List<String> values) {
        if (values.isEmpty()) {
            return null;
        }
        if (join) {
            final StringBuilder sb = new StringBuilder(1_024);

            for (int i = 0; i < values.size(); i++) {
                final String processedValue = trim ? values.get(i).trim() : values.get(i);
                sb.append(processedValue);
                if (i < values.size() - 1) { // Append separator only if not the last element
                    sb.append(joinSeparator);
                }
            }

            return sb.toString();
        } else {
            final String value = values.get(0);
            return trim ? value.trim() : value;
        }
    }

    /**
     * Prepare path to the current element.
     *
     * @param parentName     path to parent element
     * @param currentElement XML element name
     *
     * @return path to the current element
     */
    private static String getParentName(final String parentName, final String currentElement) {
        final StringBuilder sb = new StringBuilder();
        if (parentName != null) {
            sb.append(parentName);
        }
        sb.append("/").append(currentElement);
        return sb.toString();
    }

    /**
     * Adds a single value of XML item. Only columns contained in array are
     * added to the values map.
     *
     * @param elementName name of XML element
     * @param value       value to be added
     * @param values      map for storing values
     */
    private void processValue(String elementName, String value, Map<String, List<String>> values) {
        List<String> elementValues = values.computeIfAbsent(elementName, k -> new ArrayList<String>(10));
        
        elementValues.add(value);
    }
}
