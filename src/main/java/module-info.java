module com.github.peter277.xml2table {

    // Third party dependencies
    requires info.picocli;
    requires com.ctc.wstx; // Woodstox XML
    
    exports com.github.peter277.xml2table;
    // Allow PicoCLI to use reflection on the classes in the CLI package
    opens com.github.peter277.xml2table to info.picocli;
}
