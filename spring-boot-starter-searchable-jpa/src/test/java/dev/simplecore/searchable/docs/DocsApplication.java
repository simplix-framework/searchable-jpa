package dev.simplecore.searchable.docs;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application the documentation examples run in. Every Java listing in the documentation is a
 * region of a source file in this package, and {@code DocumentationExamplesTest} sends the requests
 * the documentation shows to this application.
 */
@SpringBootApplication
public class DocsApplication {
}
