package com.ujcms.core.lucene.domain;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.apache.lucene.document.Field.Store;

/**
 * 自定义字段
 *
 * @author PONY
 */
public class WebPageWithCustoms extends WebPage {
    /**
     * BigDecimal 保留 4 位效数，转换为 Long 保存，需要乘于 10000。
     */
    public static final int SCALING_FACTOR = 10000;

    @Override
    protected void fillWithDocument(Document doc) {
        super.fillWithDocument(doc);
        fetchLongs(doc, Arrays.asList(this::setI1, this::setI2, this::setI3, this::setI4, this::setI5, this::setI6));
        fetchBigDecimals(doc, Arrays.asList(this::setN1, this::setN2, this::setN3, this::setN4));
        fetchKeywords(doc, Arrays.asList(this::setK1, this::setK2, this::setK3, this::setK4,
                this::setK5, this::setK6, this::setK7, this::setK8));
        fetchStrings(doc, Arrays.asList(this::setS1, this::setS2, this::setS3, this::setS4, this::setS5, this::setS6));
        fetchBooleans(doc, Arrays.asList(this::setB1, this::setB2, this::setB3, this::setB4));
        fetchDates(doc, Arrays.asList(this::setD1, this::setD2, this::setD3, this::setD4));
    }

    @Override
    public Document toDocument() {
        Document doc = super.toDocument();
        addLongs(doc, new Long[]{i1, i2, i3, i4, i5, i6});
        addBigDecimals(doc, new BigDecimal[]{n1, n2, n3, n4});
        addKeywords(doc, new String[]{k1, k2, k3, k4, k5, k6, k7, k8});
        addTexts(doc, new String[]{s1, s2, s3, s4, s5, s6});
        addBooleans(doc, new Boolean[]{b1, b2, b3, b4});
        addDates(doc, new OffsetDateTime[]{d1, d2, d3, d4});
        return doc;
    }

    private static void fetchLongs(Document doc, List<Consumer<Long>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            Optional.ofNullable(doc.getField("i" + i)).map(IndexableField::numericValue).map(Number::longValue)
                    .ifPresent(consumers.get(i));
        }
    }

    private static void fetchBigDecimals(Document doc, List<Consumer<BigDecimal>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            Optional.ofNullable(doc.getField("n" + i)).map(IndexableField::numericValue).map(Number::longValue)
                    .map(val -> BigDecimal.valueOf(val, SCALING_FACTOR)).ifPresent(consumers.get(i));
        }
    }

    private static void fetchStrings(Document doc, List<Consumer<String>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            Optional.ofNullable(doc.get("s" + i)).ifPresent(consumers.get(i));
        }
    }

    private static void fetchKeywords(Document doc, List<Consumer<String>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            consumers.get(i).accept(doc.get("k" + i));
        }
    }

    private static void fetchBooleans(Document doc, List<Consumer<Boolean>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            Optional.ofNullable(doc.get("b" + i)).map(Boolean::valueOf).ifPresent(consumers.get(i));
        }
    }

    private static void fetchDates(Document doc, List<Consumer<OffsetDateTime>> consumers) {
        for (int i = 0, len = consumers.size(); i < len; i += 1) {
            Optional.ofNullable(doc.getField("d" + i)).map(IndexableField::numericValue).map(Number::longValue)
                    .map(Instant::ofEpochMilli).map(val -> OffsetDateTime.ofInstant(val, ZoneId.systemDefault()))
                    .ifPresent(consumers.get(i));
        }
    }

    private static void addLongs(Document doc, Long[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (values[i] != null) {
                String field = "i" + i;
                doc.add(new LongPoint(field, values[i]));
                doc.add(new StoredField(field, values[i]));
                doc.add(new NumericDocValuesField(field, values[i]));
            }
        }
    }

    private static void addBigDecimals(Document doc, BigDecimal[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (values[i] != null) {
                String field = "n" + i;
                long scaledValue = values[i].multiply(BigDecimal.valueOf(SCALING_FACTOR)).longValue();
                doc.add(new LongPoint(field, scaledValue));
                doc.add(new StoredField(field, scaledValue));
                doc.add(new NumericDocValuesField(field, scaledValue));
            }
        }
    }

    private static void addKeywords(Document doc, String[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (StringUtils.isNotBlank(values[i])) {
                doc.add(new StringField("k" + i, values[i], Store.YES));
            }
        }
    }

    private static void addTexts(Document doc, String[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (StringUtils.isNotBlank(values[i])) {
                doc.add(new TextField("s" + i, values[i], Store.YES));
            }
        }
    }

    private static void addBooleans(Document doc, Boolean[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (values[i] != null) {
                doc.add(new StringField("b" + i, values[i].toString(), Store.YES));
            }
        }
    }

    private static void addDates(Document doc, OffsetDateTime[] values) {
        for (int i = 0, len = values.length; i < len; i += 1) {
            if (values[i] != null) {
                String field = "d" + i;
                long milli = values[i].toInstant().toEpochMilli();
                doc.add(new LongPoint(field, milli));
                doc.add(new StoredField(field, milli));
                doc.add(new NumericDocValuesField(field, milli));
            }
        }
    }

    @Field(type = FieldType.Long)
    @Nullable
    private Long i1;
    @Field(type = FieldType.Long)
    @Nullable
    private Long i2;
    @Field(type = FieldType.Long)
    @Nullable
    private Long i3;
    @Field(type = FieldType.Long)
    @Nullable
    private Long i4;
    @Field(type = FieldType.Long)
    @Nullable
    private Long i5;
    @Field(type = FieldType.Long)
    @Nullable
    private Long i6;

    @Field(type = FieldType.Scaled_Float, scalingFactor = SCALING_FACTOR)
    @Nullable
    private BigDecimal n1;
    @Field(type = FieldType.Scaled_Float, scalingFactor = SCALING_FACTOR)
    @Nullable
    private BigDecimal n2;
    @Field(type = FieldType.Scaled_Float, scalingFactor = SCALING_FACTOR)
    @Nullable
    private BigDecimal n3;
    @Field(type = FieldType.Scaled_Float, scalingFactor = SCALING_FACTOR)
    @Nullable
    private BigDecimal n4;

    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k1;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k2;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k3;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k4;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k5;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k6;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k7;
    @Field(type = FieldType.Keyword, ignoreAbove = 256)
    @Nullable
    private String k8;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s1;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s2;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s3;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s4;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s5;
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    @Nullable
    private String s6;

    @Field(type = FieldType.Boolean)
    @Nullable
    private Boolean b1;
    @Field(type = FieldType.Boolean)
    @Nullable
    private Boolean b2;
    @Field(type = FieldType.Boolean)
    @Nullable
    private Boolean b3;
    @Field(type = FieldType.Boolean)
    @Nullable
    private Boolean b4;

    @Field(type = FieldType.Date)
    @Nullable
    private OffsetDateTime d1;
    @Field(type = FieldType.Date)
    @Nullable
    private OffsetDateTime d2;
    @Field(type = FieldType.Date)
    @Nullable
    private OffsetDateTime d3;
    @Field(type = FieldType.Date)
    @Nullable
    private OffsetDateTime d4;

    @Nullable
    public Long getI1() {
        return i1;
    }

    public void setI1(@Nullable Long i1) {
        this.i1 = i1;
    }

    @Nullable
    public Long getI2() {
        return i2;
    }

    public void setI2(@Nullable Long i2) {
        this.i2 = i2;
    }

    @Nullable
    public Long getI3() {
        return i3;
    }

    public void setI3(@Nullable Long i3) {
        this.i3 = i3;
    }

    @Nullable
    public Long getI4() {
        return i4;
    }

    public void setI4(@Nullable Long i4) {
        this.i4 = i4;
    }

    @Nullable
    public Long getI5() {
        return i5;
    }

    public void setI5(@Nullable Long i5) {
        this.i5 = i5;
    }

    @Nullable
    public Long getI6() {
        return i6;
    }

    public void setI6(@Nullable Long i6) {
        this.i6 = i6;
    }

    @Nullable
    public BigDecimal getN1() {
        return n1;
    }

    public void setN1(@Nullable BigDecimal n1) {
        this.n1 = n1;
    }

    @Nullable
    public BigDecimal getN2() {
        return n2;
    }

    public void setN2(@Nullable BigDecimal n2) {
        this.n2 = n2;
    }

    @Nullable
    public BigDecimal getN3() {
        return n3;
    }

    public void setN3(@Nullable BigDecimal n3) {
        this.n3 = n3;
    }

    @Nullable
    public BigDecimal getN4() {
        return n4;
    }

    public void setN4(@Nullable BigDecimal n4) {
        this.n4 = n4;
    }

    @Nullable
    public String getK1() {
        return k1;
    }

    public void setK1(@Nullable String k1) {
        this.k1 = k1;
    }

    @Nullable
    public String getK2() {
        return k2;
    }

    public void setK2(@Nullable String k2) {
        this.k2 = k2;
    }

    @Nullable
    public String getK3() {
        return k3;
    }

    public void setK3(@Nullable String k3) {
        this.k3 = k3;
    }

    @Nullable
    public String getK4() {
        return k4;
    }

    public void setK4(@Nullable String k4) {
        this.k4 = k4;
    }

    @Nullable
    public String getK5() {
        return k5;
    }

    public void setK5(@Nullable String k5) {
        this.k5 = k5;
    }

    @Nullable
    public String getK6() {
        return k6;
    }

    public void setK6(@Nullable String k6) {
        this.k6 = k6;
    }

    @Nullable
    public String getK7() {
        return k7;
    }

    public void setK7(@Nullable String k7) {
        this.k7 = k7;
    }

    @Nullable
    public String getK8() {
        return k8;
    }

    public void setK8(@Nullable String k8) {
        this.k8 = k8;
    }

    @Nullable
    public String getS1() {
        return s1;
    }

    public void setS1(@Nullable String s1) {
        this.s1 = s1;
    }

    @Nullable
    public String getS2() {
        return s2;
    }

    public void setS2(@Nullable String s2) {
        this.s2 = s2;
    }

    @Nullable
    public String getS3() {
        return s3;
    }

    public void setS3(@Nullable String s3) {
        this.s3 = s3;
    }

    @Nullable
    public String getS4() {
        return s4;
    }

    public void setS4(@Nullable String s4) {
        this.s4 = s4;
    }

    @Nullable
    public String getS5() {
        return s5;
    }

    public void setS5(@Nullable String s5) {
        this.s5 = s5;
    }

    @Nullable
    public String getS6() {
        return s6;
    }

    public void setS6(@Nullable String s6) {
        this.s6 = s6;
    }

    @Nullable
    public Boolean getB1() {
        return b1;
    }

    public void setB1(@Nullable Boolean b1) {
        this.b1 = b1;
    }

    @Nullable
    public Boolean getB2() {
        return b2;
    }

    public void setB2(@Nullable Boolean b2) {
        this.b2 = b2;
    }

    @Nullable
    public Boolean getB3() {
        return b3;
    }

    public void setB3(@Nullable Boolean b3) {
        this.b3 = b3;
    }

    @Nullable
    public Boolean getB4() {
        return b4;
    }

    public void setB4(@Nullable Boolean b4) {
        this.b4 = b4;
    }

    @Nullable
    public OffsetDateTime getD1() {
        return d1;
    }

    public void setD1(@Nullable OffsetDateTime d1) {
        this.d1 = d1;
    }

    @Nullable
    public OffsetDateTime getD2() {
        return d2;
    }

    public void setD2(@Nullable OffsetDateTime d2) {
        this.d2 = d2;
    }

    @Nullable
    public OffsetDateTime getD3() {
        return d3;
    }

    public void setD3(@Nullable OffsetDateTime d3) {
        this.d3 = d3;
    }

    @Nullable
    public OffsetDateTime getD4() {
        return d4;
    }

    public void setD4(@Nullable OffsetDateTime d4) {
        this.d4 = d4;
    }
}
