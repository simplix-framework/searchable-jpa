package dev.simplecore.searchable.web;

import dev.simplecore.searchable.core.exception.SearchableException;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a search request the client got wrong with status 400 and a problem detail.
 *
 * <p>A request that cannot be read or does not pass validation is the client's to correct, and the
 * message says what was wrong with it. The advice has the lowest precedence, so a handler the
 * application declares for the same exceptions is chosen first.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class SearchableExceptionHandler {

    /**
     * @param exception what was wrong with the request
     * @return the problem detail sent as the response body
     */
    @ExceptionHandler({SearchableValidationException.class, SearchableParseException.class})
    public ProblemDetail invalidSearchRequest(SearchableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle(MessageUtils.getMessage("web.problem.invalid.search"));
        return problem;
    }
}
