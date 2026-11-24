package dev.simplecore.searchable.docs;

import dev.simplecore.searchable.core.annotation.SearchableParams;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Aggregate;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.AggregateValue;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.Op;
import dev.simplecore.searchable.core.service.aggregate.SearchAggregator.ValueCount;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter.TimeBucket;
import dev.simplecore.searchable.core.service.bucket.TimeWindowParams;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostStatsController {

    private final SearchAggregator aggregator;
    private final TimeBucketCounter counter;

    public PostStatsController(SearchAggregator aggregator, TimeBucketCounter counter) {
        this.aggregator = aggregator;
        this.counter = counter;
    }

    // docs:start per-value
    @GetMapping("/stats/status")
    public List<ValueCount> perStatus(@SearchableParams SearchCondition<PostSearchDTO> condition) {
        return aggregator.countByValue(Post.class, condition, null, PostSearchDTO.class, "status");
    }
    // docs:end per-value

    // docs:start figures
    @GetMapping("/stats/views")
    public List<AggregateValue> viewFigures(@SearchableParams SearchCondition<PostSearchDTO> condition) {
        return aggregator.aggregate(Post.class, condition, null, PostSearchDTO.class,
                List.of(new Aggregate("viewCount", Op.SUM), new Aggregate("viewCount", Op.AVG),
                        new Aggregate("viewCount", Op.MAX)));
    }
    // docs:end figures

    // docs:start server-figures
    public List<AggregateValue> newestAndTotal(SearchCondition<PostSearchDTO> condition) {
        return aggregator.aggregate(Post.class, condition, null,
                List.of(new Aggregate("createdAt", Op.MAX), new Aggregate("id", Op.COUNT)));
    }
    // docs:end server-figures

    // docs:start counts
    @GetMapping("/counts")
    public List<Long> counts(@RequestParam Instant from, @RequestParam Instant to, @RequestParam int buckets,
                             @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params) {
        SearchCondition<PostSearchDTO> condition =
                new SearchableParamsParser<>(PostSearchDTO.class).convert(TimeWindowParams.strip(params));
        return counter.count(Post.class, condition, null, "createdAt", from, to, buckets);
    }
    // docs:end counts

    // docs:start calendar
    public List<TimeBucket> perDay(SearchCondition<PostSearchDTO> condition, Instant from, Instant to, ZoneId zone) {
        return counter.countByCalendar(Post.class, condition, null, "createdAt", from, to, ChronoUnit.DAYS, zone);
    }
    // docs:end calendar
}
