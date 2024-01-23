package com.ujcms.cms.core.lucene;

import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.commons.lucene.LuceneOperations;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static com.ujcms.cms.core.lucene.domain.EsArticle.*;
import static org.apache.lucene.queryparser.classic.QueryParser.Operator;
import static org.apache.lucene.search.BooleanClause.Occur;

/**
 * 文章全文检索实现
 *
 * @author PONY
 */
public class ArticleLuceneImpl implements ArticleLucene {
    private final LuceneOperations operations;
    private final Analyzer analyzer;
    private final Analyzer smartAnalyzer;

    public ArticleLuceneImpl(LuceneOperations operations, Analyzer analyzer, Analyzer smartAnalyzer) {
        this.operations = operations;
        this.analyzer = analyzer;
        this.smartAnalyzer = smartAnalyzer;
    }

    @Override
    public void save(EsArticle entity) {
        operations.addDocument(entity.toDocument());
    }

    @Override
    public void update(EsArticle entity) {
        operations.updateDocument(new Term(EsArticle.FIELD_ID, String.valueOf(entity.getId())), entity.toDocument());
    }

    @Override
    public void deleteById(Integer id) {
        operations.deleteDocuments(new Term(EsArticle.FIELD_ID, String.valueOf(id)));
    }

    @Override
    public void deleteBySiteId(Integer siteId) {
        operations.deleteDocuments(IntPoint.newExactQuery(FIELD_SITE_ID, siteId));
    }

    @Override
    public void deleteAll() {
        operations.deleteAll();
    }

    @Override
    public Page<EsArticle> findAll(ArticleLuceneArgs args, @Nullable Map<String, Object> queryMap, Pageable pageable) {
        BooleanQuery.Builder bool = new BooleanQuery.Builder();
        Operator operator = args.isOperatorAnd() ? Operator.AND : Operator.OR;
        try {
            if (StringUtils.isNotBlank(args.getTitle()) || StringUtils.isNotBlank(args.getBody())) {
                BooleanQuery.Builder sub = new BooleanQuery.Builder();
                if (StringUtils.isNotBlank(args.getTitle())) {
                    QueryParser titleParser = new QueryParser(FIELD_TITLE, smartAnalyzer);
                    titleParser.setDefaultOperator(operator);
                    // 标题比正文的权重高 5 倍
                    sub.add(new BoostQuery(titleParser.parse(QueryParserBase.escape(args.getTitle())), 5), Occur.SHOULD);
                }
                if (StringUtils.isNotBlank(args.getBody())) {
                    QueryParser bodyParser = new QueryParser(FIELD_BODY, smartAnalyzer);
                    bodyParser.setDefaultOperator(operator);
                    sub.add(bodyParser.parse(QueryParserBase.escape(args.getBody())), Occur.SHOULD);
                }
                bool.add(sub.build(), Occur.MUST);
            }

            if (args.getChannelId() != null) {
                bool.add(IntPoint.newExactQuery(args.isIncludeSubChannel() ? FIELD_CHANNEL_PATHS_ID : FIELD_CHANNEL_ID,
                        args.getChannelId()), Occur.MUST);
            } else if (args.getSiteId() != null) {
                bool.add(IntPoint.newExactQuery(args.isIncludeSubSite() ? FIELD_SITE_PATHS_ID : FIELD_SITE_ID,
                        args.getSiteId()), Occur.MUST);
            }

            OffsetDateTime beginPublishDate = args.getBeginPublishDate();
            OffsetDateTime endPublishDate = args.getEndPublishDate();
            if (beginPublishDate != null || endPublishDate != null) {
                bool.add(LongPoint.newRangeQuery(EsArticle.FIELD_PUBLISH_DATE,
                        beginPublishDate != null ? beginPublishDate.toInstant().toEpochMilli() : Long.MIN_VALUE,
                        endPublishDate != null ? endPublishDate.toInstant().toEpochMilli() - 1 : Long.MAX_VALUE),
                        Occur.MUST);
            }

            if (args.getWithImage() != null) {
                bool.add(new NormsFieldExistsQuery(FIELD_IMAGE),
                        Boolean.TRUE.equals(args.getWithImage()) ? Occur.MUST : Occur.MUST_NOT);
            }

            if (CollectionUtils.isNotEmpty(args.getExcludeIds())) {
                bool.add(IntPoint.newSetQuery(EsArticle.FIELD_ID, args.getExcludeIds()), Occur.MUST_NOT);
            }

            if (CollectionUtils.isNotEmpty(args.getStatus())) {
                bool.add(IntPoint.newSetQuery(FIELD_STATUS, args.getStatus()), Occur.MUST);
            }

            if (args.getEnabled() != null) {
                bool.add(new TermQuery(new Term(FIELD_ENABLED, args.getEnabled().toString())), Occur.MUST);
            }

            String[] strings = new String[]{args.getS1(), args.getS2(), args.getS3(),
                    args.getS4(), args.getS5(), args.getS6()};
            for (int i = 0, len = strings.length; i < len; i++) {
                if (strings[i] != null) {
                    QueryParser parser = new QueryParser("s" + i, smartAnalyzer);
                    parser.setDefaultOperator(operator);
                    bool.add(parser.parse(strings[i]), Occur.MUST);
                }
            }

            keywordQuery(bool, new String[]{args.getK1(), args.getK2(), args.getK3(), args.getK4(),
                    args.getK5(), args.getK6(), args.getK7(), args.getK8()});
            booleanQuery(bool, new Boolean[]{args.getB1(), args.getB2(), args.getB3(), args.getB4()});
            queryMapQuery(bool, queryMap);

            Query query = bool.build();
            Page<EsArticle> page = operations.page(query, pageable, Sort.RELEVANCE, EsArticle::of);
            // 处理高亮
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<em>", "</em>");
            Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
            highlighter.setTextFragmenter(new SimpleFragmenter(args.getFragmentSize()));
            for (EsArticle esArticle : page.getContent()) {
                esArticle.setHighlightTitle(highlighter.getBestFragment(analyzer, FIELD_TITLE, esArticle.getTitle()));
                if (StringUtils.isNotBlank(esArticle.getBody())) {
                    esArticle.setHighlightBody(highlighter.getBestFragment(analyzer, FIELD_BODY, esArticle.getBody()));
                }
            }
            return page;
        } catch (
                Exception e) {
            throw new IllegalStateException("Lucene parse exception", e);
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
        if (queryMap == null) {
            return;
        }
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
                throw new IllegalStateException("Value type must be BigDecimal, Number, OffsetDateTime: "
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
                        throw new IllegalStateException("Operator not support: " + op);
                }
            } else {
                bool.add(LongPoint.newExactQuery(field, value), Occur.MUST);
            }
        }
    }
}
