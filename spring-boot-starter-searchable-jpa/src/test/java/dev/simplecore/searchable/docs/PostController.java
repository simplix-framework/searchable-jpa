package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// docs:start controller
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PostResponse> list(@SearchableParams SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }

    @PostMapping("/search")
    public Page<PostResponse> search(@RequestBody SearchCondition<PostSearchDTO> condition) {
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }
    // docs:end controller

    // docs:start record
    @GetMapping("/simple")
    public Page<PostResponse> listSimple(@SearchableParams SearchCondition<PostQuery> condition) {
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }
    // docs:end record

    // docs:start raw
    @GetMapping("/raw")
    public Page<PostResponse> listRaw(
            @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params) {
        SearchCondition<PostSearchDTO> condition = new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return service.findAllWithSearch(condition).map(PostResponse::from);
    }
    // docs:end raw
// docs:start controller
}
// docs:end controller
