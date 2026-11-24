package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.entity.TestTag;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.repository.TestPostRepository;
import dev.simplecore.searchable.test.service.TestOrderItemService;
import dev.simplecore.searchable.test.service.TestPostService;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;


/**
 * A small set of posts with their authors, comments and tags, described twice: as the rows a test
 * loads and as plain values a test can filter and sort in Java to know what a search must return.
 */
@SpringBootTest(properties = {"searchable.test.h2-url=jdbc:h2:mem:result-oracle;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.connection.provider_disables_autocommit=false",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF"})
@ContextConfiguration(classes = BaseTestConfig.class)
@Transactional
abstract class ArticleSearchFixture {

    record Remark(String content, String author) {
    }

    record Article(String title, TestPostStatus status, long views, String author,
                           List<Remark> remarks, List<String> tags) {
    }


    static final List<Article> ARTICLES = List.of(
            new Article("alpha", TestPostStatus.PUBLISHED, 10, "kim",
                    List.of(new Remark("good read", "lee"), new Remark("needs work", "park")), List.of("java", "spring")),
            new Article("beta", TestPostStatus.PUBLISHED, 250, "kim",
                    List.of(new Remark("good point", "park")), List.of("java")),
            new Article("gamma", TestPostStatus.DRAFT, 5, "lee",
                    List.of(new Remark("good read", "park"), new Remark("typo in title", "lee")), List.of("spring")),
            new Article("delta", TestPostStatus.DRAFT, 800, "lee", List.of(), List.of()),
            new Article("epsilon", TestPostStatus.ARCHIVED, 40, "park",
                    List.of(new Remark("old news", "kim")), List.of("java", "legacy", "spring")),
            new Article("zeta", null, 0, "park", List.of(), List.of("legacy")),
            new Article("eta", TestPostStatus.PUBLISHED, 90, "lee",
                    List.of(new Remark("good read", "kim"), new Remark("good read", "lee"), new Remark("thanks", "park")),
                    List.of("java")),
            new Article("theta", TestPostStatus.PUBLISHED, 300, "park",
                    List.of(new Remark("thanks", "kim")), List.of()),
            new Article("iota", TestPostStatus.ARCHIVED, 1, "kim", List.of(), List.of("spring")));

    @Autowired
    EntityManager em;
    @Autowired
    TestPostService postService;
    @Autowired
    TestPostRepository postRepository;
    @Autowired
    TestOrderItemService orderItemService;

    @BeforeEach
    void loadFixture() {
        SearchableFieldUtils.clearCache();
        for (String table : List.of("test_comment", "test_post_tag", "test_post", "test_tag", "test_author",
                "test_order_note", "test_order_item")) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
        Map<String, TestAuthor> authors = new HashMap<>();
        for (String name : List.of("kim", "lee", "park")) {
            TestAuthor author = TestAuthor.builder().name(name).email(name + "@example.com").nickname(name).build();
            em.persist(author);
            authors.put(name, author);
        }
        Map<String, TestTag> tags = new HashMap<>();
        for (String name : List.of("java", "spring", "legacy")) {
            TestTag tag = TestTag.builder().name(name).build();
            em.persist(tag);
            tags.put(name, tag);
        }
        for (Article article : ARTICLES) {
            TestPost post = TestPost.builder().title(article.title()).status(article.status())
                    .viewCount(article.views()).author(authors.get(article.author())).build();
            article.tags().forEach(tag -> post.getTags().add(tags.get(tag)));
            em.persist(post);
            for (Remark remark : article.remarks()) {
                TestComment comment = new TestComment();
                comment.setContent(remark.content());
                comment.setAuthor(authors.get(remark.author()));
                comment.setPost(post);
                em.persist(comment);
            }
        }
        em.flush();
        em.clear();
    }
}
