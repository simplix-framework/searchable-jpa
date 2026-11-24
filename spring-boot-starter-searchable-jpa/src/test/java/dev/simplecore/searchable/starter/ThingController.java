package dev.simplecore.searchable.starter;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ThingController {

    static final String WRITTEN_DESCRIPTION = "Lists the things the caller may see.";

    private final ThingService service;

    public ThingController(ThingService service) {
        this.service = service;
    }

    private List<String> names(SearchCondition<ThingSearchDto> condition) {
        return service.findAllWithSearch(condition).getContent().stream().map(Thing::getName).toList();
    }

    /** The raw parameters converted by the controller, marked with the annotation's former home. */
    @SuppressWarnings("deprecation")
    @Operation(description = WRITTEN_DESCRIPTION)
    @GetMapping("/things/legacy")
    public List<String> legacy(@RequestParam
                               @dev.simplecore.searchable.openapi.annotation.SearchableParams(ThingSearchDto.class)
                               Map<String, String> params) {
        return names(new SearchableParamsParser<>(ThingSearchDto.class).convert(params));
    }

    /** The raw parameters converted by the controller. */
    @Operation(description = WRITTEN_DESCRIPTION)
    @GetMapping("/things/raw")
    public List<String> raw(@RequestParam @SearchableParams(ThingSearchDto.class) Map<String, String> filters) {
        return names(new SearchableParamsParser<>(ThingSearchDto.class).convert(filters));
    }

    /** The search resolved from the query string before the controller is called. */
    @Operation(description = WRITTEN_DESCRIPTION)
    @GetMapping("/things/resolved")
    public List<String> resolved(@SearchableParams SearchCondition<ThingSearchDto> condition) {
        return names(condition);
    }

    @PostMapping("/things/search")
    public List<String> search(@RequestBody SearchCondition<ThingSearchDto> condition) {
        return names(condition);
    }
}
