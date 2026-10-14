package com.ujcms.cms.ext.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.listener.ArticleDeleteListener;
import com.ujcms.cms.core.listener.ArticlePublishListener;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 搜索引擎主动推送（docs/spec/sitemap/plan.md D13）：文章发布后把文章地址提交给百度普通收录 API（主动推送）与
 * IndexNow（Bing、Yandex、Naver、Seznam 等共享）。sitemap 是"等引擎来抓"，推送是"告诉引擎去抓"，
 * 百度尤其依赖后者。
 * <p>
 * 事务提交后入队，后台单线程每 {@value #FLUSH_DELAY_SECONDS} 秒按站点合并成一次请求（批量发布、批量新增译文
 * 不会打出一串请求）；推送失败只记日志，绝不影响保存。百度按站点日配额计数：同一地址 24 小时内只推送一次，
 * 单次最多 {@value #BAIDU_MAX_URLS} 条；IndexNow 单次最多 {@value #INDEXNOW_MAX_URLS} 条，200/202 视为成功。
 * 网络层失败（连不上、超时）的批次回队重试，最多 {@value #MAX_ATTEMPTS} 次；引擎明确拒绝（401/403/422/429）不重试——
 * 那是配置问题，重试只会重复失败。每站点、每引擎保留最近一次推送结果（{@link #lastResult}）供控制台展示；
 * {@link #pushTest} 用已保存的设置即时推送站点首页，让错误的 token / 密钥在控制台就能看到，而不必翻应用日志。
 * <p>
 * 生成地址固定为桌面上下文（与 sitemap 同款）；静态化站点提交的是静态页地址，静态页若托管在其它域名，
 * IndexNow 会因主机不符回 422，此时应关闭 IndexNow 推送。
 *
 * @author PONY
 */
@Component
public class SearchEnginePushService implements ArticlePublishListener, ArticleDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(SearchEnginePushService.class);

    private final SiteService siteService;
    private final ArticleMapper articleMapper;
    private final HttpClient httpClient;
    private final String indexNowEndpoint;
    private final ConcurrentMap<Long, Pending> pendingBySite = new ConcurrentHashMap<>();
    /**
     * 每站点、每引擎最近一次推送结果：键为 siteId|engine。进程内存，重启即清
     */
    private final ConcurrentMap<String, PushResult> lastResults = new ConcurrentHashMap<>();
    /**
     * 百度配额保护：键为 siteId + 地址，24 小时内不重复推送（成功后才登记，失败次日可再试）
     */
    private final Cache<String, Boolean> baiduPushed = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS).maximumSize(200_000).build();
    @Nullable
    private final ScheduledExecutorService scheduler;

    @Autowired
    public SearchEnginePushService(SiteService siteService, ArticleMapper articleMapper) {
        this(siteService, articleMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build(), INDEXNOW_ENDPOINT, true);
    }

    /**
     * 测试构造：可注入 HttpClient 与 IndexNow 端点，{@code scheduled=false} 时不起后台线程，由调用方 {@link #flush()}
     */
    SearchEnginePushService(SiteService siteService, ArticleMapper articleMapper, HttpClient httpClient,
                            String indexNowEndpoint, boolean scheduled) {
        this.siteService = siteService;
        this.articleMapper = articleMapper;
        this.httpClient = httpClient;
        this.indexNowEndpoint = indexNowEndpoint;
        if (scheduled) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "search-engine-push");
                thread.setDaemon(true);
                return thread;
            });
            this.scheduler.scheduleWithFixedDelay(this::flushQuietly, FLUSH_DELAY_SECONDS, FLUSH_DELAY_SECONDS,
                    TimeUnit.SECONDS);
        } else {
            this.scheduler = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    // region listeners

    @Override
    public void afterArticlePublish(Article article) {
        Long siteId = article.getSiteId();
        Long articleId = article.getId();
        Site site = siteService.select(siteId);
        if (site == null || !isAnyEnabled(site.getGeo())) {
            return;
        }
        // 提交后再读最终行：审批流可能在保存过程中改过状态；地址组装需要栏目别名与站点配置
        afterCommit(() -> {
            Article fresh = articleMapper.select(articleId);
            if (fresh != null && fresh.isNormal() && !fresh.isLink()) {
                fresh.setSite(site);
                enqueue(siteId, desktopFullUrl(fresh), true, true);
            }
        });
    }

    @Override
    public void preArticleDelete(Long articleId) {
        Article article = articleMapper.select(articleId);
        if (article == null || !article.isNormal() || article.isLink()) {
            return;
        }
        Site site = siteService.select(article.getSiteId());
        if (site == null || !site.getGeo().isIndexNowEnabled()) {
            return;
        }
        // 彻底删除：IndexNow 接受已删除页面的通知（引擎重抓得到 404 后移除）；百度主动推送不用于删除
        article.setSite(site);
        String url = desktopFullUrl(article);
        Long siteId = site.getId();
        afterCommit(() -> enqueue(siteId, url, false, true));
    }

    private static boolean isAnyEnabled(Site.Geo geo) {
        return (geo.isBaiduPushEnabled() && StringUtils.isNotBlank(geo.getBaiduPushUrl()))
                || (geo.isIndexNowEnabled() && StringUtils.isNotBlank(geo.getIndexNowKey()));
    }

    /**
     * 桌面上下文下的完整地址：后台保存请求可能带手机 UA，静态化站点会据此组出手机页地址
     */
    private static String desktopFullUrl(Article article) {
        boolean mobile = Contexts.isMobile();
        Contexts.setMobile(false);
        try {
            return article.getFullUrl();
        } finally {
            Contexts.setMobile(mobile);
        }
    }

    private static void afterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }
    // endregion

    // region queue

    /**
     * 入队；由后台线程合并推送
     *
     * @param siteId   站点ID
     * @param url      完整地址
     * @param baidu    是否推送百度
     * @param indexNow 是否推送 IndexNow
     */
    public void enqueue(Long siteId, String url, boolean baidu, boolean indexNow) {
        Pending pending = pendingBySite.computeIfAbsent(siteId, key -> new Pending(0));
        if (baidu) {
            pending.baidu.add(url);
        }
        if (indexNow) {
            pending.indexNow.add(url);
        }
    }

    /**
     * 推送全部待推送地址：每站点、每引擎合并为一次请求（超过单次上限时分批）。后台线程定时调用；测试可直接调用。
     * 网络层失败的地址回队，下一轮再试（最多 {@value #MAX_ATTEMPTS} 次）
     */
    public void flush() {
        for (Long siteId : List.copyOf(pendingBySite.keySet())) {
            Pending pending = pendingBySite.remove(siteId);
            if (pending == null) {
                continue;
            }
            Site site = siteService.select(siteId);
            if (site == null) {
                continue;
            }
            Site.Geo geo = site.getGeo();
            Pending retry = new Pending(pending.attempt + 1);
            if (geo.isBaiduPushEnabled() && StringUtils.isNotBlank(geo.getBaiduPushUrl())) {
                List<String> candidates = new ArrayList<>(pending.baidu.size());
                for (String url : pending.baidu) {
                    if (baiduPushed.getIfPresent(site.getId() + "|" + url) == null) {
                        candidates.add(url);
                    }
                }
                for (List<String> batch : partition(candidates, BAIDU_MAX_URLS)) {
                    if (pushBaidu(site, geo.getBaiduPushUrl(), batch).transportFailed()) {
                        retry.baidu.addAll(batch);
                    }
                }
            }
            if (geo.isIndexNowEnabled() && StringUtils.isNotBlank(geo.getIndexNowKey())) {
                for (List<String> batch : partition(new ArrayList<>(pending.indexNow), INDEXNOW_MAX_URLS)) {
                    if (pushIndexNow(site, geo.getIndexNowKey(), batch).transportFailed()) {
                        retry.indexNow.addAll(batch);
                    }
                }
            }
            if (retry.isEmpty()) {
                continue;
            }
            if (retry.attempt >= MAX_ATTEMPTS) {
                logger.warn("Search engine push dropped after {} attempts: site={} baidu={} indexNow={}",
                        MAX_ATTEMPTS, siteId, retry.baidu.size(), retry.indexNow.size());
                continue;
            }
            pendingBySite.merge(siteId, retry, Pending::merge);
        }
    }

    /**
     * 站点待推送地址数（含回队重试的）。仅供测试观察队列
     */
    int pendingCount(Long siteId) {
        Pending pending = pendingBySite.get(siteId);
        return pending == null ? 0 : pending.baidu.size() + pending.indexNow.size();
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException e) {
            logger.warn("Search engine push failed", e);
        }
    }
    // endregion

    // region engines

    /**
     * 百度普通收录 API：POST text/plain，每行一个地址；响应 {@code {"remain":N,"success":N,"not_same_site":[],"not_valid":[]}}，
     * 4xx 为 {@code {"error":401,"message":"token is not valid"}}。成功（响应含 success 字段）后登记 24 小时去重
     *
     * @return 本批结果；网络层失败时 {@link PushResult#status()} 为 0
     */
    private PushResult pushBaidu(Site site, String pushUrl, List<String> batch) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(pushUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("\n", batch), StandardCharsets.UTF_8))
                .build();
        PushResult result;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            boolean ok = response.statusCode() == 200 && isBaiduSuccess(body);
            result = new PushResult(ENGINE_BAIDU, OffsetDateTime.now(), batch.size(), ok, response.statusCode(), abbreviate(body));
        } catch (IOException e) {
            result = transportFailure(ENGINE_BAIDU, batch.size(), request, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = transportFailure(ENGINE_BAIDU, batch.size(), request, e);
        }
        if (result.ok()) {
            for (String url : batch) {
                baiduPushed.put(site.getId() + "|" + url, Boolean.TRUE);
            }
            logger.info("Baidu push: site={} submitted={} response={}", site.getId(), batch.size(), result.message());
        } else {
            logger.warn("Baidu push failed: site={} submitted={} status={} response={}",
                    site.getId(), batch.size(), result.status(), result.message());
        }
        lastResults.put(resultKey(site.getId(), ENGINE_BAIDU), result);
        return result;
    }

    /**
     * IndexNow：POST JSON {@code {host, key, keyLocation, urlList}}；200 已受理，202 受理待验证密钥；
     * 403 密钥无效，422 地址与主机不符，429 限流
     *
     * @return 本批结果；网络层失败时 {@link PushResult#status()} 为 0
     */
    private PushResult pushIndexNow(Site site, String key, List<String> batch) {
        String host = host(site);
        if (host == null) {
            PushResult result = new PushResult(ENGINE_INDEX_NOW, OffsetDateTime.now(), batch.size(), false, 0,
                    "site has no resolvable host");
            logger.warn("IndexNow push skipped: site={} has no resolvable host", site.getId());
            lastResults.put(resultKey(site.getId(), ENGINE_INDEX_NOW), result);
            return result;
        }
        String keyLocation = site.getDynamicFullUrl() + "/" + INDEXNOW_KEY_FILE_PREFIX + key + ".txt";
        ObjectNode payload = Constants.MAPPER.createObjectNode();
        payload.put("host", host);
        payload.put("key", key);
        payload.put("keyLocation", keyLocation);
        ArrayNode urlList = payload.putArray("urlList");
        batch.forEach(urlList::add);
        HttpRequest request = HttpRequest.newBuilder(URI.create(indexNowEndpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        PushResult result;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = response.statusCode() == 200 || response.statusCode() == 202;
            result = new PushResult(ENGINE_INDEX_NOW, OffsetDateTime.now(), batch.size(), ok, response.statusCode(),
                    abbreviate(response.body()));
        } catch (IOException e) {
            result = transportFailure(ENGINE_INDEX_NOW, batch.size(), request, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = transportFailure(ENGINE_INDEX_NOW, batch.size(), request, e);
        }
        if (result.ok()) {
            logger.info("IndexNow push: site={} host={} submitted={} status={}",
                    site.getId(), host, batch.size(), result.status());
        } else {
            logger.warn("IndexNow push failed: site={} host={} submitted={} status={} response={}",
                    site.getId(), host, batch.size(), result.status(), result.message());
        }
        lastResults.put(resultKey(site.getId(), ENGINE_INDEX_NOW), result);
        return result;
    }

    private static PushResult transportFailure(String engine, int submitted, HttpRequest request, Exception e) {
        logger.warn("Search engine push request failed: {} {}", request.uri().getHost(), e.toString());
        return new PushResult(engine, OffsetDateTime.now(), submitted, false, 0, e.toString());
    }

    // endregion

    // region status & test

    /**
     * 站点某引擎最近一次推送结果（进程内存；尚未推送或重启后为 {@code null}）
     *
     * @param siteId 站点ID
     * @param engine {@link #ENGINE_BAIDU} 或 {@link #ENGINE_INDEX_NOW}
     */
    @Nullable
    public PushResult lastResult(Long siteId, String engine) {
        return lastResults.get(resultKey(siteId, engine));
    }

    /**
     * 推送测试：用站点已保存的设置即时提交站点首页（桌面上下文地址）。百度测试不受 24 小时去重限制，
     * 但同样占用一条当日配额。调用方须先确认该引擎已开启且已配置
     *
     * @param site   站点
     * @param engine {@link #ENGINE_BAIDU} 或 {@link #ENGINE_INDEX_NOW}
     * @return 本次推送结果
     */
    public PushResult pushTest(Site site, String engine) {
        Site.Geo geo = site.getGeo();
        String homepage = desktopFullUrl(site);
        if (ENGINE_BAIDU.equals(engine)) {
            return pushBaidu(site, Objects.requireNonNull(geo.getBaiduPushUrl(), "baiduPushUrl"), List.of(homepage));
        }
        if (ENGINE_INDEX_NOW.equals(engine)) {
            return pushIndexNow(site, Objects.requireNonNull(geo.getIndexNowKey(), "indexNowKey"), List.of(homepage));
        }
        throw new IllegalArgumentException("Unknown search engine: " + engine);
    }

    private static String desktopFullUrl(Site site) {
        boolean mobile = Contexts.isMobile();
        Contexts.setMobile(false);
        try {
            return site.getFullUrl();
        } finally {
            Contexts.setMobile(mobile);
        }
    }

    private static String resultKey(Long siteId, String engine) {
        return siteId + "|" + engine;
    }

    /**
     * 一次推送（一个批次）的结果
     *
     * @param engine    引擎：{@link #ENGINE_BAIDU} / {@link #ENGINE_INDEX_NOW}
     * @param time      推送时间
     * @param submitted 提交地址条数
     * @param ok        引擎是否受理
     * @param status    HTTP 状态码；网络层失败为 0
     * @param message   引擎响应摘要或异常描述
     */
    public record PushResult(String engine, OffsetDateTime time, int submitted, boolean ok, int status,
                             String message) {
        /**
         * 是否网络层失败（值得重试）
         */
        boolean transportFailed() {
            return status == 0 && !ok;
        }
    }
    // endregion

    // region engines (helpers)

    @Nullable
    static String host(Site site) {
        String url = site.getDynamicFullUrl();
        if (url == null) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int from = 0; from < list.size(); from += size) {
            result.add(list.subList(from, Math.min(list.size(), from + size)));
        }
        return result;
    }

    private static String abbreviate(@Nullable String body) {
        return body == null ? "" : StringUtils.abbreviate(body.replace('\n', ' '), 300);
    }

    /**
     * 供日志之外的调用方判定百度响应是否成功（{@code success} 字段存在）
     */
    static boolean isBaiduSuccess(String body) {
        try {
            JsonNode node = Constants.MAPPER.readTree(body);
            return node.has("success");
        } catch (IOException e) {
            return false;
        }
    }
    // endregion

    private static final class Pending {
        private final Set<String> baidu = ConcurrentHashMap.newKeySet();
        private final Set<String> indexNow = ConcurrentHashMap.newKeySet();
        /**
         * 已尝试推送次数（首次入队为 0，网络层失败回队时 +1）
         */
        private final int attempt;

        private Pending(int attempt) {
            this.attempt = attempt;
        }

        private boolean isEmpty() {
            return baidu.isEmpty() && indexNow.isEmpty();
        }

        /**
         * 回队与新入队合并：地址取并集，尝试次数取较大者（新入队的地址随重试批次一起发，最坏多算一次）
         */
        private static Pending merge(Pending a, Pending b) {
            Pending merged = new Pending(Math.max(a.attempt, b.attempt));
            merged.baidu.addAll(a.baidu);
            merged.baidu.addAll(b.baidu);
            merged.indexNow.addAll(a.indexNow);
            merged.indexNow.addAll(b.indexNow);
            return merged;
        }
    }

    /**
     * 密钥文件名前缀：{@code /indexnow-{key}.txt}（{@code GeoController} 提供）
     */
    public static final String INDEXNOW_KEY_FILE_PREFIX = "indexnow-";
    static final String INDEXNOW_ENDPOINT = "https://api.indexnow.org/indexnow";
    public static final String ENGINE_BAIDU = "baidu";
    public static final String ENGINE_INDEX_NOW = "indexNow";
    static final int FLUSH_DELAY_SECONDS = 5;
    /**
     * 网络层失败的最大尝试次数（含首次）
     */
    static final int MAX_ATTEMPTS = 3;
    static final int BAIDU_MAX_URLS = 2000;
    static final int INDEXNOW_MAX_URLS = 10_000;
}
