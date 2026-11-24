package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.service.SearchCursor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Window;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostReadController {

    private final PostService service;

    public PostReadController(PostService service) {
        this.service = service;
    }

    // docs:start scroll
    @GetMapping("/scroll")
    public ScrollResponse scroll(@RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params) {
        Map<String, String> filters = new LinkedHashMap<>(params);
        String cursor = filters.remove("cursor");
        SearchCondition<PostSearchDTO> condition = new SearchableParamsParser<>(PostSearchDTO.class).convert(filters);

        Window<Post> window = service.scrollWithSearch(condition, service.scrollPosition(cursor));
        String next = window.hasNext() ? SearchCursor.encode(window.positionAt(window.size() - 1)) : null;
        return new ScrollResponse(window.map(PostResponse::from).getContent(), next);
    }
    // docs:end scroll

    // docs:start project
    @GetMapping("/titles")
    public Page<PostTitle> titles(@SearchableParams SearchCondition<PostSearchDTO> condition) {
        return service.projectWithSearch(condition, PostTitle.class);
    }
    // docs:end project
}
