package com.ujcms.cms.core.lucene;

import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.lucene.domain.WebPage;
import com.ujcms.commons.lucene.ExpDecayValueSource;
import com.ujcms.commons.lucene.LuceneOperations;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
        operations.updateDocument(new Term(EsArticle.FIELD_ID, String.valueOf(entity.getId())), entity.toDocument());
    }

    @Override
    public void deleteById(Long id) {
        operations.deleteDocuments(new Term(EsArticle.FIELD_ID, String.valueOf(id)));
    }

    @Override
    public void deleteBySiteId(Long siteId) {
        operations.deleteDocuments(LongPoint.newExactQuery(FIELD_SITE_ID, siteId));
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
            titleQuery(bool, operator, args.getTitle(), args.getBody());
            channelQuery(bool, args.getChannelIds(), args.getSiteId(), args.isIncludeSubChannel(), args.isIncludeSubSite());
            publishDateQuery(bool, args.getBeginPublishDate(), args.getEndPublishDate());
            withImageQuery(bool, args.getWithImage());

            if (CollectionUtils.isNotEmpty(args.getExcludeIds())) {
                bool.add(LongPoint.newSetQuery(EsArticle.FIELD_ID, args.getExcludeIds()), Occur.MUST_NOT);
            }
            if (CollectionUtils.isNotEmpty(args.getStatus())) {
                bool.add(IntPoint.newSetQuery(FIELD_STATUS, args.getStatus()), Occur.FILTER);
            }
            if (args.getEnabled() != null) {
                bool.add(new TermQuery(new Term(FIELD_ENABLED, args.getEnabled().toString())), Occur.FILTER);
            }
            decayQuery(bool, args.getDateExpScale(), args.getDateExpOffset(), args.getDateExpDecay(), args.getDateExpBoost());

            String[] strings = new String[]{args.getS1(), args.getS2(), args.getS3(),
                    args.getS4(), args.getS5(), args.getS6()};
            for (int i = 0, len = strings.length; i < len; i++) {
                if (strings[i] != null) {
                    QueryParser parser = new QueryParser("s" + i, analyzer);
                    parser.setDefaultOperator(operator);
                    bool.add(parser.parse(strings[i]), Occur.MUST);
                }
            }

            keywordQuery(bool, new String[]{args.getK1(), args.getK2(), args.getK3(), args.getK4(),
                    args.getK5(), args.getK6(), args.getK7(), args.getK8()});
            booleanQuery(bool, new Boolean[]{args.getB1(), args.getB2(), args.getB3(), args.getB4()});
            mapQuery(bool, queryMap);

            Query query = bool.build();
            Sort sort = toLuceneSort(pageable.getSort());
            return operations.page(query, pageable, sort, args.getFragmentSize(), EsArticle::of);
        } catch (Exception e) {
            throw new IllegalStateException("Lucene parse exception", e);
        }
    }

    private void titleQuery(BooleanQuery.Builder bool, Operator operator,
                            @Nullable String title, @Nullable String body)
            throws ParseException {
        if (StringUtils.isNotBlank(title) || StringUtils.isNotBlank(body)) {
            BooleanQuery.Builder sub = new BooleanQuery.Builder();
            if (StringUtils.isNotBlank(title)) {
                QueryParser titleParser = new QueryParser(FIELD_TITLE, analyzer);
                titleParser.setDefaultOperator(operator);
                // 标题比正文的权重高 3 倍
                sub.add(new BoostQuery(titleParser.parse(QueryParserBase.escape(title)), 3), Occur.SHOULD);
            }
            if (StringUtils.isNotBlank(body)) {
                QueryParser bodyParser = new QueryParser(FIELD_BODY, analyzer);
                bodyParser.setDefaultOperator(operator);
                sub.add(bodyParser.parse(QueryParserBase.escape(body)), Occur.SHOULD);
            }
            bool.add(sub.build(), Occur.MUST);
        }
    }

    private void channelQuery(BooleanQuery.Builder bool, @Nullable Collection<Long> channelIds, @Nullable Long siteId,
                              boolean isIncludeSubChannel, boolean isIncludeSubSite) {
        if (CollectionUtils.isNotEmpty(channelIds)) {
            BooleanQuery.Builder sub = new BooleanQuery.Builder();
            String channelField = isIncludeSubChannel ? EsArticle.FIELD_CHANNEL_PATHS_ID : EsArticle.FIELD_CHANNEL_ID;
            for (Long channelId : channelIds) {
                sub.add(LongPoint.newExactQuery(channelField, channelId), Occur.SHOULD);
            }
            bool.add(sub.build(), Occur.FILTER);
        } else if (siteId != null) {
            String siteField = isIncludeSubSite ? WebPage.FIELD_SITE_PATHS_ID : WebPage.FIELD_SITE_ID;
            bool.add(LongPoint.newExactQuery(siteField, siteId), Occur.FILTER);
        }
    }

    private void decayQuery(BooleanQuery.Builder bool, int scale, int offset, double decay, float boost) {
        if (scale > 0) {
            long dayMillis = 24L * 60 * 60 * 1000;
            LongFieldSource publishDateSource = new LongFieldSource(FIELD_PUBLISH_DATE);
            long now = System.currentTimeMillis();
            ValueSource decaySource = new ExpDecayValueSource(publishDateSource, now,
                    scale * dayMillis, offset * dayMillis, decay);
            Query decayQuery = new BoostQuery(new FunctionQuery(decaySource), boost);
            bool.add(decayQuery, Occur.SHOULD);
        }
    }

    private void publishDateQuery(BooleanQuery.Builder bool, @Nullable OffsetDateTime beginPublishDate,
                                  @Nullable OffsetDateTime endPublishDate) {
        if (beginPublishDate != null || endPublishDate != null) {
            long begin = beginPublishDate != null ? beginPublishDate.toInstant().toEpochMilli() : Long.MIN_VALUE;
            long end = endPublishDate != null ? endPublishDate.toInstant().toEpochMilli() - 1 : Long.MAX_VALUE;
            bool.add(LongPoint.newRangeQuery(FIELD_PUBLISH_DATE, begin, end), Occur.FILTER);
        }
    }

    private void withImageQuery(BooleanQuery.Builder bool, @Nullable Boolean withImage) {
        if (withImage != null) {
            Occur occur = Boolean.TRUE.equals(withImage) ? Occur.FILTER : Occur.MUST_NOT;
            bool.add(new NormsFieldExistsQuery(FIELD_IMAGE), occur);
        }
    }

    private static void keywordQuery(BooleanQuery.Builder bool, String[] values) {
        for (int i = 0, len = values.length; i < len; i++) {
            if (values[i] != null) {
                bool.add(new TermQuery(new Term("k" + i, values[i])), Occur.FILTER);
            }
        }
    }

    private static void booleanQuery(BooleanQuery.Builder bool, Boolean[] values) {
        for (int i = 0, len = values.length; i < len; i++) {
            if (values[i] != null) {
                bool.add(new TermQuery(new Term("b" + i, values[i].toString())), Occur.FILTER);
            }
        }
    }

    @Nullable
    private static Sort toLuceneSort(org.springframework.data.domain.Sort sort) {
        if (sort.isUnsorted()) {
            return null;
        }
        List<SortField> sortFields = new ArrayList<>();
        for (org.springframework.data.domain.Sort.Order order : sort) {
            String property = order.getProperty();
            sortFields.add(new SortField(property, EsArticle.getSortType(property), order.isDescending()));
        }
        return new Sort(sortFields.toArray(new SortField[0]));
    }

    private static void mapQuery(BooleanQuery.Builder bool, @Nullable Map<String, Object> queryMap) {
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
            long value = mapQueryValue(obj);
            if (fieldAndOperator.length > 1) {
                String operator = fieldAndOperator[1];
                mapQueryOperator(bool, operator, field, value);
            } else {
                bool.add(LongPoint.newExactQuery(field, value), Occur.FILTER);
            }
        }
    }

    private static void mapQueryOperator(BooleanQuery.Builder bool, String operator, String field, long value) {
        switch (operator) {
            case "gt":
                bool.add(LongPoint.newRangeQuery(field, value + 1, Long.MAX_VALUE), Occur.FILTER);
                break;
            case "gte":
                bool.add(LongPoint.newRangeQuery(field, value, Long.MAX_VALUE), Occur.FILTER);
                break;
            case "lt":
                bool.add(LongPoint.newRangeQuery(field, Long.MIN_VALUE, value - 1), Occur.FILTER);
                break;
            case "lte":
                bool.add(LongPoint.newRangeQuery(field, Long.MIN_VALUE, value), Occur.FILTER);
                break;
            default:
                throw new IllegalStateException("Operator not support: " + operator);
        }
    }

    private static long mapQueryValue(Object obj) {
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).multiply(BigDecimal.valueOf(SCALING_FACTOR)).longValue();
        } else if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else if (obj instanceof OffsetDateTime) {
            return ((OffsetDateTime) obj).toInstant().toEpochMilli();
        } else {
            throw new IllegalStateException("Value type must be BigDecimal, Number, OffsetDateTime: "
                    + obj.getClass().getName());
        }
    }
}
