package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.dto.TestOrderItemSearchDTO;
import dev.simplecore.searchable.test.dto.TestPostToManySortDTO;
import dev.simplecore.searchable.test.entity.TestOrderItem;
import dev.simplecore.searchable.test.entity.TestOrderNote;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sorting through a collection, and a composite-key entity: the order and the rows a search returns
 * are compared with the order worked out in Java over the same fixture.
 */
class SearchSortOracleTest extends ArticleSearchFixture {

    @Test
    @DisplayName("sorting through a collection orders each row once, by its smallest element")
    void sortingThroughACollection() {
        List<String> expected = ARTICLES.stream()
                .filter(a -> a.status() == TestPostStatus.PUBLISHED)
                .sorted(Comparator.comparing((Article a) -> a.remarks().stream().map(Remark::content).min(String::compareTo).orElseThrow())
                        .thenComparing(a -> ARTICLES.indexOf(a)))
                .map(Article::title).toList();

        List<String> seen = new ArrayList<>();
        long total = -1;
        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            SearchCondition<TestPostToManySortDTO> condition = SearchConditionBuilder.create(TestPostToManySortDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("commentContent")).page(pageNumber).size(2).build();
            Page<TestPost> page = postService.findAllWithSearch(condition);
            page.getContent().forEach(post -> seen.add(post.getTitle()));
            total = page.getTotalElements();
        }

        assertThat(seen).containsExactlyElementsOf(expected);
        assertThat(total).isEqualTo(expected.size());
    }

    @Test
    @DisplayName("a sort through a collection the conditions also filter reads the elements they matched")
    void sortingThroughAFilteredCollection() {
        Predicate<Remark> wanted = remark -> remark.content().contains("good") || remark.content().contains("thanks");
        for (boolean ascending : List.of(true, false)) {
            Comparator<String> direction = ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
            List<String> expected = ARTICLES.stream()
                    .filter(a -> a.remarks().stream().anyMatch(wanted))
                    .sorted(Comparator.comparing((Article a) -> a.remarks().stream().filter(wanted)
                                    .map(Remark::content).min(direction).orElseThrow(), direction)
                            .thenComparing(a -> ARTICLES.indexOf(a)))
                    .map(Article::title).toList();

            List<String> seen = new ArrayList<>();
            for (int pageNumber = 0; pageNumber * 2 < expected.size(); pageNumber++) {
                SearchCondition<TestPostToManySortDTO> condition = SearchConditionBuilder.create(TestPostToManySortDTO.class)
                        .where(w -> w.contains("commentContent", "good").orContains("commentContent", "thanks"))
                        .sort(s -> { if (ascending) { s.asc("commentContent"); } else { s.desc("commentContent"); } })
                        .page(pageNumber).size(2).build();
                Page<TestPost> page = postService.findAllWithSearch(condition);
                page.getContent().forEach(post -> seen.add(post.getTitle()));
                assertThat(page.getTotalElements()).isEqualTo(expected.size());
            }
            assertThat(seen).as(ascending ? "ascending" : "descending").containsExactlyElementsOf(expected);
            em.clear();
        }
    }

    @Test
    @DisplayName("a composite-key entity gives the same rows through a collection condition")
    void compositeKeyEntity() {
        Map<String, List<String>> notesByItem = new LinkedHashMap<>();
        notesByItem.put("bolt", List.of("fragile", "rush order"));
        notesByItem.put("nut", List.of("rush order"));
        notesByItem.put("washer", List.of());
        notesByItem.put("gear", List.of("fragile", "fragile handling", "weekend delivery"));
        long line = 1;
        for (Map.Entry<String, List<String>> entry : notesByItem.entrySet()) {
            TestOrderItem item = new TestOrderItem();
            item.setOrderItemId(new TestOrderItem.OrderItemKey("W" + (line % 2), line++));
            item.setName(entry.getKey());
            for (String content : entry.getValue()) {
                TestOrderNote note = new TestOrderNote();
                note.setContent(content);
                item.addNote(note);
            }
            em.persist(item);
        }
        em.flush();
        em.clear();

        SearchCondition<TestOrderItemSearchDTO> fragile = SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                .where(w -> w.contains("noteContent", "fragile")).page(0).size(1).build();
        List<String> names = new ArrayList<>();
        Page<TestOrderItem> page = orderItemService.findAllWithSearch(fragile);
        while (true) {
            page.getContent().forEach(item -> names.add(item.getName()));
            assertThat(page.getTotalElements()).isEqualTo(2);
            if (!page.hasNext()) {
                break;
            }
            fragile.setPage(fragile.getPage() + 1);
            page = orderItemService.findAllWithSearch(fragile);
        }
        assertThat(names).containsExactlyInAnyOrder("bolt", "gear");
        assertThat(orderItemService.countWithSearch(fragile)).isEqualTo(2);
        assertThat(orderItemService.existsWithSearch(fragile)).isTrue();

        SearchCondition<TestOrderItemSearchDTO> either = SearchConditionBuilder.create(TestOrderItemSearchDTO.class)
                .where(w -> w.equals("name", "washer").orContains("noteContent", "rush")).build();
        assertThat(orderItemService.findAllWithSearch(either).getContent()).extracting(TestOrderItem::getName)
                .containsExactlyInAnyOrder("bolt", "nut", "washer");
        assertThat(orderItemService.countWithSearch(either)).isEqualTo(3);
    }
}
