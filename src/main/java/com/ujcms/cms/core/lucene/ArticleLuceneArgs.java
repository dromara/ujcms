package com.ujcms.cms.core.lucene;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * @author PONY
 */
public class ArticleLuceneArgs {
    /**
     * 查询标题
     */
    @Nullable
    private String title;
    /**
     * 查询内容
     */
    @Nullable
    private String body;
    /**
     * 高亮内容长度（默认100）
     */
    private int fragmentSize = 100;
    /**
     * 是否每个分词都必须匹配
     */
    private boolean operatorAnd = false;
    /**
     * 站点ID
     */
    @Nullable
    private Long siteId;
    /**
     * 是否包含子站点
     */
    private boolean includeSubSite = false;
    /**
     * 栏目ID
     */
    @Nullable
    private Collection<Long> channelIds;
    /**
     * 是否包含子栏目
     */
    private boolean includeSubChannel = true;
    /**
     * 日期衰减半径。为 0 时不启用日期衰减，默认为 0。单位：天
     */
    private int dateExpScale = 0;
    /**
     * 日期衰减偏移量。单位：天
     */
    private int dateExpOffset = 0;
    /**
     * 日期衰减率。默认为 0.5。取值范围：(0, 1)
     */
    private double dateExpDecay = 0.5;
    /**
     * 日期衰减权重提升。默认为 3
     */
    private float dateExpBoost = 3;
    /**
     * 起始发布日期
     */
    @Nullable
    private OffsetDateTime beginPublishDate;
    /**
     * 截止发布日期
     */
    @Nullable
    private OffsetDateTime endPublishDate;
    /**
     * 是否有图片
     */
    @Nullable
    private Boolean withImage;
    /**
     * 需排除的文章ID
     */
    @Nullable
    private Collection<Long> excludeIds;
    /**
     * 状态
     */
    @Nullable
    private Collection<Integer> status;
    /**
     * 是否启用
     */
    @Nullable
    private Boolean enabled;

    @Nullable
    private String k1;
    @Nullable
    private String k2;
    @Nullable
    private String k3;
    @Nullable
    private String k4;
    @Nullable
    private String k5;
    @Nullable
    private String k6;
    @Nullable
    private String k7;
    @Nullable
    private String k8;
    @Nullable
    private String s1;
    @Nullable
    private String s2;
    @Nullable
    private String s3;
    @Nullable
    private String s4;
    @Nullable
    private String s5;
    @Nullable
    private String s6;
    @Nullable
    private Boolean b1;
    @Nullable
    private Boolean b2;
    @Nullable
    private Boolean b3;
    @Nullable
    private Boolean b4;

    @Nullable
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    @Nullable
    public String getBody() {
        return body;
    }

    public void setBody(@Nullable String body) {
        this.body = body;
    }

    public int getFragmentSize() {
        return fragmentSize;
    }

    public void setFragmentSize(int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    public boolean isOperatorAnd() {
        return operatorAnd;
    }

    public void setOperatorAnd(boolean operatorAnd) {
        this.operatorAnd = operatorAnd;
    }

    @Nullable
    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(@Nullable Long siteId) {
        this.siteId = siteId;
    }

    public boolean isIncludeSubSite() {
        return includeSubSite;
    }

    public void setIncludeSubSite(boolean includeSubSite) {
        this.includeSubSite = includeSubSite;
    }

    @Nullable
    public Collection<Long> getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(@Nullable Collection<Long> channelIds) {
        this.channelIds = channelIds;
    }

    public boolean isIncludeSubChannel() {
        return includeSubChannel;
    }

    public void setIncludeSubChannel(boolean includeSubChannel) {
        this.includeSubChannel = includeSubChannel;
    }

    public int getDateExpScale() {
        return dateExpScale;
    }

    public void setDateExpScale(int dateExpScale) {
        this.dateExpScale = dateExpScale;
    }

    public int getDateExpOffset() {
        return dateExpOffset;
    }

    public void setDateExpOffset(int dateExpOffset) {
        this.dateExpOffset = dateExpOffset;
    }

    public double getDateExpDecay() {
        return dateExpDecay;
    }

    public void setDateExpDecay(double dateExpDecay) {
        this.dateExpDecay = dateExpDecay;
    }

    public float getDateExpBoost() {
        return dateExpBoost;
    }

    public void setDateExpBoost(float dateExpBoost) {
        this.dateExpBoost = dateExpBoost;
    }

    @Nullable
    public OffsetDateTime getBeginPublishDate() {
        return beginPublishDate;
    }

    public void setBeginPublishDate(@Nullable OffsetDateTime beginPublishDate) {
        this.beginPublishDate = beginPublishDate;
    }

    @Nullable
    public OffsetDateTime getEndPublishDate() {
        return endPublishDate;
    }

    public void setEndPublishDate(@Nullable OffsetDateTime endPublishDate) {
        this.endPublishDate = endPublishDate;
    }

    @Nullable
    public Boolean getWithImage() {
        return withImage;
    }

    public void setWithImage(@Nullable Boolean withImage) {
        this.withImage = withImage;
    }

    @Nullable
    public Collection<Long> getExcludeIds() {
        return excludeIds;
    }

    public void setExcludeIds(@Nullable Collection<Long> excludeIds) {
        this.excludeIds = excludeIds;
    }

    @Nullable
    public Collection<Integer> getStatus() {
        return status;
    }

    public void setStatus(@Nullable Collection<Integer> status) {
        this.status = status;
    }

    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(@Nullable Boolean enabled) {
        this.enabled = enabled;
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
}
