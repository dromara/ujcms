package com.ujcms.core.lucene;

import com.ofwise.util.lucene.LuceneOperations;
import com.ujcms.core.lucene.domain.EsArticle;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.NormsFieldExistsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;

import static com.ujcms.core.lucene.domain.EsArticle.*;
import static org.apache.lucene.queryparser.classic.QueryParser.Operator;
import static org.apache.lucene.search.BooleanClause.Occur;

/**
 * 文章全文检索实现
 *
 * @author PONY
 */
public class ArticleLuceneImpl implements ArticleLucene {
    private LuceneOperations operations;
    private Analyzer analyzer;

    public ArticleLuceneImpl(LuceneOperations operations, Analyzer analyzer) {
        this.operations = operations;
        this.analyzer = analyzer;
    }

    @Override
    public void save(EsArticle entity) {
        operations.addDocument(entity.toDocument());
    }

    @Override
    public void update(EsArticle entity) {
        operations.updateDocument(new Term(EsArticle.ID, String.valueOf(entity.getId())), entity.toDocument());
    }

    @Override
    public void deleteById(Integer id) {
        operations.deleteDocuments(new Term(EsArticle.ID, String.valueOf(id)));
    }

    @Override
    public void deleteBySiteId(Integer siteId) {
        operations.deleteDocuments(IntPoint.newExactQuery(SITE_ID, siteId));
    }

    @Override
    public void deleteAll() {
        operations.deleteAll();
    }

    @Override
    public Page<EsArticle> findAll(@Nullable String q, boolean isIncludeBody, boolean isOperatorAnd,
                                   @Nullable Integer siteId, boolean isIncludeSubSite,
                                   @Nullable Integer channelId, boolean isIncludeSubChannel,
                                   @Nullable OffsetDateTime beginPublishDate, @Nullable OffsetDateTime endPublishDate,
                                   @Nullable Boolean isWithImage, @Nullable Collection<Integer> excludeIds,
                                   @Nullable String k1, @Nullable String k2, @Nullable String k3, @Nullable String k4,
                                   @Nullable String k5, @Nullable String k6, @Nullable String k7, @Nullable String k8,
                                   @Nullable String s1, @Nullable String s2, @Nullable String s3, @Nullable String s4,
                                   @Nullable String s5, @Nullable String s6,
                                   @Nullable Boolean b1, @Nullable Boolean b2,
                                   @Nullable Boolean b3, @Nullable Boolean b4,
                                   @Nullable Map<String, Object> queryMap, Pageable pageable) {
        BooleanQuery.Builder bool = new BooleanQuery.Builder();
        Operator operator = isOperatorAnd ? Operator.AND : Operator.OR;
        try {
            if (StringUtils.isNotBlank(q)) {
                q = QueryParser.escape(q);
                QueryParser titleParser = new QueryParser(TITLE, analyzer);
                titleParser.setDefaultOperator(operator);
                BooleanQuery.Builder sub = new BooleanQuery.Builder()
                        // 标题比正文的权重高 5 倍
                        .add(new BoostQuery(titleParser.parse(q), 5), Occur.SHOULD);
                if (isIncludeBody) {
                    QueryParser bodyParser = new QueryParser(BODY, analyzer);
                    bodyParser.setDefaultOperator(operator);
                    sub.add(bodyParser.parse(q), Occur.SHOULD);
                }
                bool.add(sub.build(), Occur.MUST);
            }

            if (channelId != null) {
                bool.add(IntPoint.newExactQuery(isIncludeSubChannel ? CHANNEL_PATHS_ID : CHANNEL_ID, channelId),
                        Occur.MUST);
            } else if (siteId != null) {
                bool.add(IntPoint.newExactQuery(isIncludeSubSite ? SITE_PATHS_ID : SITE_ID, siteId), Occur.MUST);
            }

            if (beginPublishDate != null || endPublishDate != null) {
                bool.add(LongPoint.newRangeQuery(PUBLISH_DATE,
                        beginPublishDate != null ? beginPublishDate.toInstant().toEpochMilli() : Long.MIN_VALUE,
                        endPublishDate != null ? endPublishDate.toInstant().toEpochMilli() - 1 : Long.MAX_VALUE),
                        Occur.MUST);
            }

            if (isWithImage != null) {
                bool.add(new NormsFieldExistsQuery(IMAGE), Occur.MUST);
            }

            if (CollectionUtils.isNotEmpty(excludeIds)) {
                bool.add(IntPoint.newSetQuery(ID, excludeIds), Occur.MUST_NOT);
            }

            String[] strings = new String[]{s1, s2, s3, s4, s5, s6};
            for (int i = 0, len = strings.length; i < len; i++) {
                if (strings[i] != null) {
                    QueryParser parser = new QueryParser("s" + i, analyzer);
                    parser.setDefaultOperator(operator);
                    bool.add(parser.parse(strings[i]), Occur.MUST);
                }
            }

            keywordQuery(bool, new String[]{k1, k2, k3, k4, k5, k6, k7, k8});
            booleanQuery(bool, new Boolean[]{b1, b2, b3, b4});
            queryMapQuery(bool, queryMap);

            Query query = bool.build();
            Page<EsArticle> page = operations.page(query, pageable, Sort.RELEVANCE, EsArticle::of);
            // 处理高亮
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<em>", "</em>");
            Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
            highlighter.setTextFragmenter(new SimpleFragmenter());
            for (EsArticle esArticle : page.getContent()) {
                esArticle.setHighlightTitle(highlighter.getBestFragment(analyzer, TITLE, esArticle.getTitle()));
                if (StringUtils.isNotBlank(esArticle.getBody())) {
                    esArticle.setHighlightBody(highlighter.getBestFragment(analyzer, BODY, esArticle.getBody()));
                }
            }
            return page;
        } catch (Exception e) {
            throw new RuntimeException("Lucene parse exception", e);
        }
    }

    private static void keywordQuery(BooleanQuery.Builder bool, String[] values) {
        for (int i = 0, len = values.length; i < len; i++) {
            if (values[i] != null) {
                bool.add(new TermQuery(new Term("k" + i, values[i])), Occur.MUST);
            }
        }
    }

    private static void booleanQuery(BooleanQuery.Builder bool, Boolean[] values) {
        for (int i = 0, len = values.length; i < len; i++) {
            if (values[i] != null) {
                bool.add(new TermQuery(new Term("b" + i, values[i].toString())), Occur.MUST);
            }
        }
    }

    private static void queryMapQuery(BooleanQuery.Builder bool, @Nullable Map<String, Object> queryMap) {
        if (queryMap != null) {
            for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
                // 支持 i1-gt: 5 或 i2: 12
                String[] fieldAndOperator = entry.getKey().split("-");
                String field = fieldAndOperator[0];
                Object obj = entry.getValue();
                // 只支持两个字母，如 i1, n1, d2 等
                if (field.length() > 2 || obj == null) {
                    continue;
                }
                long value;
                if (obj instanceof BigDecimal) {
                    value = ((BigDecimal) obj).multiply(BigDecimal.valueOf(SCALING_FACTOR)).longValue();
                } else if (obj instanceof Number) {
                    value = ((Number) obj).longValue();
                } else if (obj instanceof OffsetDateTime) {
                    value = ((OffsetDateTime) obj).toInstant().toEpochMilli();
                } else {
                    throw new RuntimeException("Value type must be BigDecimal, Number, OffsetDateTime: "
                            + obj.getClass().getName());
                }
                if (fieldAndOperator.length > 1) {
                    String op = fieldAndOperator[1];
                    switch (op) {
                        case "gt":
                            bool.add(LongPoint.newRangeQuery(field, value + 1, Long.MAX_VALUE), Occur.MUST);
                            break;
                        case "gte":
                            bool.add(LongPoint.newRangeQuery(field, value, Long.MAX_VALUE), Occur.MUST);
                            break;
                        case "lt":
                            bool.add(LongPoint.newRangeQuery(field, Long.MIN_VALUE, value - 1), Occur.MUST);
                            break;
                        case "lte":
                            bool.add(LongPoint.newRangeQuery(field, Long.MIN_VALUE, value), Occur.MUST);
                            break;
                        default:
                            throw new RuntimeException("Operator not support: " + op);
                    }
                } else {
                    bool.add(LongPoint.newExactQuery(field, value), Occur.MUST);
                }
            }

        }
    }
}
