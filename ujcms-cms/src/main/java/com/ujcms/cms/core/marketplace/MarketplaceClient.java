package com.ujcms.cms.core.marketplace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Props;

/**
 * 应用市场实例 API 客户端（remote-marketplace C1）。目录读取（列表 / 详情 / 版本 / 更新检查）、
 * 匿名免费下载授权与签名包下载，形状对齐 {@code contracts/marketplace-api.yaml}
 * （本仓库 vendored 副本见 {@code ujcms-cms/src/test/resources/marketplace/}，D-C1-2）。
 *
 * <p>行为要点：
 * <ul>
 * <li>每次请求携带 {@code X-UJCMS-Version}（当前运行版本），供服务端按 {@code minUjcmsVersion}
 * 做兼容过滤；</li>
 * <li>目录读取带短 TTL Caffeine 缓存（{@code ujcms.marketplace.cache-ttl-seconds}，默认 60 秒）；</li>
 * <li>预览图 / 截图 / 下载授权 URL 是不透明的相对引用（服务端 D-M3-3）——只做「相对 base-url 解析」，
 * 绝不自行构造；</li>
 * <li>网络故障 / 5xx / 429 映射为 {@link MarketplaceUnavailableException}（429 解析
 * {@code Retry-After}，退避而非报错，D-C1-3）。</li>
 * </ul>
 */
@Component
public class MarketplaceClient {
    private static final Logger logger = LoggerFactory.getLogger(MarketplaceClient.class);

    /** 实例每次请求携带的运行版本头（kickoff §3） */
    public static final String HEADER_UJCMS_VERSION = "X-UJCMS-Version";
    /** 目录/资产的公开文件前缀：资产代理只允许该前缀下的相对路径（服务端 D-M3-3，包文件除外） */
    private static final String ASSET_PATH_PREFIX = "/files/items/";
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int CATALOG_READ_TIMEOUT_MILLIS = 10_000;
    private static final int DOWNLOAD_READ_TIMEOUT_MILLIS = 60_000;
    private static final long CACHE_MAX_ENTRIES = 500;

    private final boolean enabled;
    private final String baseUrl;
    private final Props props;
    private final RestClient catalogClient;
    private final RestClient downloadClient;
    private final Cache<String, Object> cache;

    @Autowired
    public MarketplaceClient(RestClient.Builder restClientBuilder, Props props,
                             @Value("${ujcms.marketplace.enabled:true}") boolean enabled,
                             @Value("${ujcms.marketplace.base-url:https://marketplace.ujcms.com}") String baseUrl,
                             @Value("${ujcms.marketplace.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this(buildClient(restClientBuilder, baseUrl, CATALOG_READ_TIMEOUT_MILLIS),
                buildClient(restClientBuilder, baseUrl, DOWNLOAD_READ_TIMEOUT_MILLIS),
                props, enabled, baseUrl, cacheTtlSeconds);
    }

    /** 测试接缝：直接注入（可绑 MockRestServiceServer 的）RestClient 实例 */
    MarketplaceClient(RestClient catalogClient, RestClient downloadClient, Props props,
                      boolean enabled, String baseUrl, long cacheTtlSeconds) {
        this.catalogClient = catalogClient;
        this.downloadClient = downloadClient;
        this.props = props;
        this.enabled = enabled;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.cache = Caffeine.newBuilder()
                .maximumSize(CACHE_MAX_ENTRIES)
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .build();
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl, int readTimeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(readTimeoutMillis);
        return builder.clone()
                .baseUrl(stripTrailingSlash(baseUrl))
                .requestFactory(requestFactory)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    // ----- catalog reads（短 TTL 缓存） --------------------------------------

    /**
     * 目录列表（{@code GET /api/v1/catalog/items}）。{@code kind}/{@code sort} 直接透传
     * 契约线值（{@code full}/{@code data-only}、{@code newest}/{@code popular}/{@code name}），
     * 非法值由服务端 400（其 D-M3-6 严格解析）。
     */
    public CatalogItemPage listItems(@Nullable String q, @Nullable String kind, @Nullable String tag,
                                     @Nullable String sort, int page, int pageSize) {
        String key = "items?q=" + q + "&kind=" + kind + "&tag=" + tag + "&sort=" + sort
                + "&page=" + page + "&pageSize=" + pageSize;
        return cached(key, CatalogItemPage.class, () -> catalogClient.get()
                .uri(builder -> {
                    UriBuilder b = builder.path("/api/v1/catalog/items")
                            .queryParam("page", page)
                            .queryParam("pageSize", pageSize);
                    if (q != null && !q.isBlank()) {
                        b.queryParam("q", q);
                    }
                    if (kind != null && !kind.isBlank()) {
                        b.queryParam("kind", kind);
                    }
                    if (tag != null && !tag.isBlank()) {
                        b.queryParam("tag", tag);
                    }
                    if (sort != null && !sort.isBlank()) {
                        b.queryParam("sort", sort);
                    }
                    return b.build();
                })
                .header(HEADER_UJCMS_VERSION, props.getVersion())
                .retrieve()
                .body(CatalogItemPage.class));
    }

    /** 条目详情（{@code GET /api/v1/catalog/items/{slug}}），含已发布版本摘要。未发布/不存在 → 404 上抛 */
    public CatalogItemDetail getItem(String slug) {
        return cached("item:" + slug, CatalogItemDetail.class, () -> catalogClient.get()
                .uri("/api/v1/catalog/items/{slug}", slug)
                .header(HEADER_UJCMS_VERSION, props.getVersion())
                .retrieve()
                .body(CatalogItemDetail.class));
    }

    /** 已发布版本列表（{@code GET /api/v1/catalog/items/{slug}/versions}，语义化版本新在前） */
    public List<ItemVersion> listVersions(String slug) {
        return cachedList("versions:" + slug, () -> catalogClient.get()
                .uri("/api/v1/catalog/items/{slug}/versions", slug)
                .header(HEADER_UJCMS_VERSION, props.getVersion())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ItemVersion>>() {
                }));
    }

    /**
     * 更新检查（{@code POST /api/v1/catalog/update-check}，C2 画廊角标的数据源）。
     * 逐条降级语义在服务端：未知/未发布/版本不可解析 → 该条 {@code updateAvailable=false}。不缓存。
     */
    public List<UpdateCheckResult> updateCheck(List<UpdateCheckEntry> entries) {
        try {
            List<UpdateCheckResult> results = catalogClient.post()
                    .uri("/api/v1/catalog/update-check")
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .body(entries)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UpdateCheckResult>>() {
                    });
            return results == null ? List.of() : results;
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    // ----- assets（控制台资产代理的取数侧，D-C1-4） ---------------------------

    /**
     * 拉取目录资产（预览图/截图）。{@code path} 是目录响应里的不透明相对引用，只允许
     * {@code /files/items/**}——包文件必须走下载授权（服务端 D-M3-3），本方法绝不代取。
     */
    public Asset fetchAsset(String path) {
        requireRelativeFilePath(path, ASSET_PATH_PREFIX, "marketplace.error.assetNotFound");
        try {
            return catalogClient.get()
                    .uri(path)
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new MarketplaceException("marketplace.error.assetNotFound",
                                    "Marketplace asset '" + path + "' responded " + response.getStatusCode());
                        }
                        HttpHeaders headers = response.getHeaders();
                        return new Asset(response.getBody().readAllBytes(),
                                headers.getFirst(HttpHeaders.CONTENT_TYPE),
                                headers.getFirst(HttpHeaders.CACHE_CONTROL));
                    });
        } catch (MarketplaceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    // ----- downloads（D-K7：sha256 贯穿始终） --------------------------------

    /**
     * 申请下载授权（{@code POST /api/v1/downloads}）。免费条目匿名可得（C1）；付费条目须携带
     * 持有效授权账号的 Bearer 令牌（C2/M5），匿名/未购买一律 403 {@code not_entitled} →
     * {@code marketplace.error.notEntitled}。
     */
    public DownloadGrant requestDownload(String item, @Nullable String version) {
        return requestDownload(item, version, null);
    }

    /**
     * 携带 Bearer 令牌的下载授权（C2）：付费条目按账号授权放行（D-K6），免费条目的下载日志
     * 归因到账号与实例。401 → {@link MarketplaceAuthException}（调用方刷新后重试）；
     * 403 按 problem+json {@code code} 区分 {@code insufficient_scope}（重连）与
     * {@code not_entitled}（未购买）。
     */
    public DownloadGrant requestDownload(String item, @Nullable String version, @Nullable String accessToken) {
        try {
            DownloadGrant grant = catalogClient.post()
                    .uri("/api/v1/downloads")
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .headers(headers -> {
                        if (accessToken != null) {
                            headers.setBearerAuth(accessToken);
                        }
                    })
                    .body(new DownloadRequest(item, version))
                    .retrieve()
                    .body(DownloadGrant.class);
            if (grant == null || grant.url() == null || grant.sha256() == null) {
                throw new MarketplaceException("marketplace.error.downloadFailed",
                        "Download grant for '" + item + "' is missing url/sha256");
            }
            return grant;
        } catch (HttpClientErrorException.NotFound e) {
            throw new MarketplaceException("marketplace.error.itemNotFound",
                    "Marketplace item or version not found: " + item
                            + (version == null ? "" : " " + version), e);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new MarketplaceAuthException(MarketplaceAuthException.Kind.UNAUTHORIZED,
                    "Download grant rejected: access token invalid or expired");
        } catch (HttpClientErrorException.Forbidden e) {
            if (isInsufficientScope(e)) {
                throw new MarketplaceAuthException(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE,
                        "Download grant rejected: token lacks the download scope");
            }
            throw new MarketplaceException("marketplace.error.notEntitled",
                    "No entitlement for paid item: " + item, e);
        } catch (MarketplaceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    /**
     * 按授权下载包到临时文件，边写边算 sha256（散列即落盘字节的散列）。授权 URL 是签名的
     * 相对引用，仅相对 base-url 解析。成功后临时文件归调用方清理；失败本方法自清。
     */
    public DownloadedPackage downloadPackage(DownloadGrant grant) {
        String url = grant.url();
        requireRelativeFilePath(url, "/files/", "marketplace.error.downloadFailed");
        Path tempZip;
        try {
            tempZip = Files.createTempFile("ujcms-market-", ".zip");
        } catch (IOException e) {
            throw new MarketplaceException("marketplace.error.downloadFailed",
                    "Failed to create temp file: " + e.getMessage(), e);
        }
        try {
            return downloadClient.get()
                    .uri(url)
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            // 403 invalid_signature / 410 url_expired / 404（如占位存储键）——一并可诉
                            throw new MarketplaceException("marketplace.error.downloadFailed",
                                    "Package fetch responded " + response.getStatusCode() + " for " + url);
                        }
                        MessageDigest digest = sha256Digest();
                        try (InputStream in = response.getBody();
                             OutputStream out = new DigestOutputStream(Files.newOutputStream(tempZip), digest)) {
                            in.transferTo(out);
                        }
                        return new DownloadedPackage(tempZip,
                                HexFormat.of().formatHex(digest.digest()), Files.size(tempZip));
                    });
        } catch (RuntimeException e) {
            FileUtils.deleteQuietly(tempZip.toFile());
            if (e instanceof MarketplaceException me) {
                throw me;
            }
            if (e instanceof ResourceAccessException) {
                throw new MarketplaceException("marketplace.error.downloadFailed",
                        "Package download failed: " + e.getMessage(), e);
            }
            throw mapUnavailable(e);
        }
    }

    // ----- oauth / account（C2：设备授权流 RFC 8628 + 刷新轮换 + 账号面） ------

    /** 注册在市场授权服务器上的唯一公共客户端（服务端 M4） */
    public static final String OAUTH_CLIENT_ID = "ujcms-instance";
    /** 实例连接请求的 scope 集合（D-K4） */
    public static final String OAUTH_SCOPES = "catalog:read entitlements:read download";
    private static final String GRANT_TYPE_DEVICE_CODE = "urn:ietf:params:oauth:grant-type:device_code";

    /**
     * 发起设备授权流（{@code POST /api/v1/oauth/device/code}，RFC 8628 §3.1）。实例元数据
     * （名称/域名/{@code X-UJCMS-Version}）在批准页展示并于批准时记录为已连接实例（D-K4）。
     */
    public DeviceAuthorization requestDeviceCode(@Nullable String instanceName, @Nullable String instanceDomain) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", OAUTH_CLIENT_ID);
        form.add("scope", OAUTH_SCOPES);
        if (instanceName != null && !instanceName.isBlank()) {
            form.add("instance_name", instanceName.trim());
        }
        if (instanceDomain != null && !instanceDomain.isBlank()) {
            form.add("instance_domain", instanceDomain.trim());
        }
        DeviceAuthorization authorization = postOAuthForm("/api/v1/oauth/device/code", form, DeviceAuthorization.class);
        if (authorization == null || authorization.deviceCode() == null || authorization.userCode() == null) {
            throw new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                    "Marketplace device authorization response is incomplete", (Throwable) null);
        }
        return authorization;
    }

    /**
     * 轮询设备授权令牌（{@code POST /api/v1/oauth/token}，RFC 8628 §3.4）。轮询语义经
     * {@link MarketplaceOAuthException} 上抛：{@code authorization_pending}（继续）、
     * {@code slow_down}（间隔 +5s）、{@code access_denied} / {@code expired_token}（终止）。
     */
    public OAuthTokens pollDeviceToken(String deviceCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", GRANT_TYPE_DEVICE_CODE);
        form.add("device_code", deviceCode);
        form.add("client_id", OAUTH_CLIENT_ID);
        return requireTokens(postOAuthForm("/api/v1/oauth/token", form, OAuthTokens.class));
    }

    /**
     * 刷新访问令牌（RFC 6749 §6）。refresh token 每次使用即轮换（单次有效，服务端 D-M4-1）——
     * 调用方必须先将返回的新令牌对持久化、再作废旧令牌；{@code invalid_grant} 表示旧 refresh
     * token 已死（吊销/竞态），连接须重建。
     */
    public OAuthTokens refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", OAUTH_CLIENT_ID);
        return requireTokens(postOAuthForm("/api/v1/oauth/token", form, OAuthTokens.class));
    }

    /**
     * 吊销令牌（{@code POST /api/v1/oauth/revoke}，RFC 7009）。吊销 refresh token 即断开连接；
     * 未知令牌同样应答 200（幂等）。
     */
    public void revokeToken(String token, @Nullable String tokenTypeHint) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        if (tokenTypeHint != null) {
            form.add("token_type_hint", tokenTypeHint);
        }
        form.add("client_id", OAUTH_CLIENT_ID);
        postOAuthForm("/api/v1/oauth/revoke", form, Void.class);
    }

    /** 连接账号 + 实例信息（{@code GET /api/v1/me}；每次调用刷新实例心跳） */
    public Me getMe(String accessToken) {
        return requireBody(bearerGet("/api/v1/me", accessToken, Me.class), "/api/v1/me");
    }

    /**
     * 连接账号的有效授权分页（{@code GET /api/v1/me/entitlements}，scope
     * {@code entitlements:read}；仅 ACTIVE，免费条目从不出现——无需购买即可下载，D-K6）。
     */
    public EntitlementPage listEntitlements(String accessToken, int page, int pageSize) {
        return requireBody(bearerGet("/api/v1/me/entitlements?page=" + page + "&pageSize=" + pageSize,
                accessToken, EntitlementPage.class), "/api/v1/me/entitlements");
    }

    /** OAuth 表单请求：400 解析 RFC 错误 JSON 上抛 {@link MarketplaceOAuthException}，网络/5xx/429 走统一不可用映射 */
    @Nullable
    private <T> T postOAuthForm(String path, MultiValueMap<String, String> form, Class<T> type) {
        try {
            return catalogClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .body(form)
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException.BadRequest | HttpClientErrorException.Unauthorized e) {
            throw parseOAuthError(e);
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    private static OAuthTokens requireTokens(@Nullable OAuthTokens tokens) {
        if (tokens == null || tokens.accessToken() == null) {
            throw new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                    "Marketplace token response is incomplete", (Throwable) null);
        }
        return tokens;
    }

    private static <T> T requireBody(@Nullable T body, String path) {
        if (body == null) {
            throw new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                    "Marketplace returned an empty response for " + path, (Throwable) null);
        }
        return body;
    }

    /** Bearer 资源调用：401 → UNAUTHORIZED（可刷新重试）；403 insufficient_scope → 须重连 */
    @Nullable
    private <T> T bearerGet(String path, String accessToken, Class<T> type) {
        try {
            return catalogClient.get()
                    .uri(path)
                    .header(HEADER_UJCMS_VERSION, props.getVersion())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(type);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new MarketplaceAuthException(MarketplaceAuthException.Kind.UNAUTHORIZED,
                    "Marketplace rejected the access token for " + path);
        } catch (HttpClientErrorException.Forbidden e) {
            if (isInsufficientScope(e)) {
                throw new MarketplaceAuthException(MarketplaceAuthException.Kind.INSUFFICIENT_SCOPE,
                        "Marketplace token lacks the required scope for " + path);
            }
            throw e;
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    private static MarketplaceOAuthException parseOAuthError(HttpClientErrorException e) {
        try {
            JsonNode node = Constants.MAPPER.readTree(e.getResponseBodyAsByteArray());
            String error = node.path("error").asText(null);
            if (error != null && !error.isBlank()) {
                return new MarketplaceOAuthException(error, node.path("error_description").asText(null));
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through：非 RFC 错误体按原状态码归入 invalid_request
        }
        return new MarketplaceOAuthException("invalid_request",
                "Unparseable OAuth error response (" + e.getStatusCode() + ")");
    }

    /** 403 problem+json 的 {@code code} 判别：{@code insufficient_scope} 与业务性拒绝（如 not_entitled）分流 */
    private static boolean isInsufficientScope(HttpClientErrorException e) {
        try {
            JsonNode node = Constants.MAPPER.readTree(e.getResponseBodyAsByteArray());
            return "insufficient_scope".equals(node.path("code").asText(null));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    // ----- helpers ----------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Class<T> type, Supplier<@Nullable T> loader) {
        Object hit = cache.getIfPresent(key);
        if (type.isInstance(hit)) {
            return (T) hit;
        }
        T value = load(loader);
        cache.put(key, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cachedList(String key, Supplier<@Nullable List<T>> loader) {
        Object hit = cache.getIfPresent(key);
        if (hit instanceof List<?> list) {
            return (List<T>) list;
        }
        List<T> value = load(loader);
        cache.put(key, value);
        return value;
    }

    private <T> T load(Supplier<@Nullable T> loader) {
        try {
            T value = loader.get();
            if (value == null) {
                throw new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                        "Marketplace returned an empty response", (Throwable) null);
            }
            return value;
        } catch (RuntimeException e) {
            throw mapUnavailable(e);
        }
    }

    /**
     * 统一的不可用映射：连接/超时（ResourceAccessException）与 5xx → OFFLINE；
     * 429 → RATE_LIMITED（解析 {@code Retry-After}）。其余 4xx 原样上抛（调用方按语境处理）。
     */
    private RuntimeException mapUnavailable(RuntimeException e) {
        if (e instanceof MarketplaceException || e instanceof MarketplaceUnavailableException) {
            return e;
        }
        if (e instanceof ResourceAccessException) {
            logger.warn("Marketplace unreachable at {}: {}", baseUrl, e.getMessage());
            return new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                    "Marketplace unreachable: " + e.getMessage(), e);
        }
        if (e instanceof HttpServerErrorException) {
            logger.warn("Marketplace server error at {}: {}", baseUrl, e.getMessage());
            return new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.OFFLINE,
                    "Marketplace server error: " + e.getMessage(), e);
        }
        if (e instanceof HttpClientErrorException he && he.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            Integer retryAfter = parseRetryAfter(he.getResponseHeaders());
            return new MarketplaceUnavailableException(MarketplaceUnavailableException.Reason.RATE_LIMITED,
                    "Marketplace rate limit hit", retryAfter);
        }
        return e;
    }

    @Nullable
    private static Integer parseRetryAfter(@Nullable HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 相对引用防护：必须以指定前缀开头，禁绝对 URL / 协议 / 上跳 / 反斜杠——URL 只解析、不构造，
     * 且资产代理不可被诱导访问任意主机或包路径。
     */
    private static void requireRelativeFilePath(String path, String requiredPrefix, String errorCode) {
        if (!path.startsWith(requiredPrefix) || path.contains("..") || path.contains("://")
                || path.contains("\\") || path.length() > 512) {
            throw new MarketplaceException(errorCode, "Illegal marketplace file reference: " + path);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----- 契约 DTO（marketplace-api.yaml components/schemas 的手写映射；未知字段忽略以向前兼容） -----

    /** 目录列表条目（contract {@code CatalogItem}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogItem(String slug, String type, String kind, String name,
                              @Nullable String summary, @Nullable List<String> tags, int priceCents,
                              @Nullable String previewUrl, @Nullable ItemVersionSummary latestVersion,
                              @Nullable String publishedAt, @Nullable String updatedAt) {
    }

    /** 目录分页（contract {@code CatalogItemPage}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogItemPage(int page, int pageSize, long total, int totalPages,
                                  List<CatalogItem> items) {
    }

    /** 条目详情（contract {@code CatalogItemDetail}；records 无继承，字段平铺） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogItemDetail(String slug, String type, String kind, String name,
                                    @Nullable String summary, @Nullable List<String> tags, int priceCents,
                                    @Nullable String previewUrl, @Nullable ItemVersionSummary latestVersion,
                                    @Nullable String publishedAt, @Nullable String updatedAt,
                                    @Nullable String description, @Nullable List<Screenshot> screenshots,
                                    @Nullable List<ItemVersionSummary> versions) {
    }

    /** 截图（contract {@code Screenshot}；url 为不透明相对引用） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Screenshot(String url, @Nullable Integer sort) {
    }

    /** 版本摘要（contract {@code ItemVersionSummary}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemVersionSummary(String version, @Nullable String minUjcmsVersion,
                                     @Nullable String publishedAt) {
    }

    /** 版本明细（contract {@code ItemVersion}：+changelog/sha256/size） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemVersion(String version, @Nullable String minUjcmsVersion,
                              @Nullable String publishedAt, @Nullable String changelog,
                              String sha256, long size) {
    }

    /** 更新检查请求条目（contract {@code UpdateCheckEntry}） */
    public record UpdateCheckEntry(String name, String version) {
    }

    /** 更新检查结果（contract {@code UpdateCheckResult}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateCheckResult(String name, String installedVersion,
                                    @Nullable ItemVersionSummary latestVersion, boolean updateAvailable) {
    }

    /** 下载授权请求（contract {@code DownloadRequest}） */
    public record DownloadRequest(String item, @Nullable String version) {
    }

    /** 设备授权应答（contract {@code DeviceAuthorizationResponse}，RFC 8628 §3.2 snake_case 线格式） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceAuthorization(@JsonProperty("device_code") String deviceCode,
                                      @JsonProperty("user_code") String userCode,
                                      @JsonProperty("verification_uri") String verificationUri,
                                      @JsonProperty("verification_uri_complete") String verificationUriComplete,
                                      @JsonProperty("expires_in") int expiresIn,
                                      @JsonProperty("interval") int interval) {
    }

    /** 令牌应答（contract {@code TokenResponse}，RFC 6749 §5.1；refresh token 单次有效、每次轮换） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OAuthTokens(@JsonProperty("access_token") String accessToken,
                              @JsonProperty("token_type") @Nullable String tokenType,
                              @JsonProperty("expires_in") long expiresIn,
                              @JsonProperty("refresh_token") @Nullable String refreshToken,
                              @JsonProperty("scope") @Nullable String scope) {
    }

    /** 连接账号 + 实例（contract {@code Me}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Me(MeAccount account, @Nullable MeInstance instance) {
    }

    /** 账号（contract {@code Account}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeAccount(String id, String email, @Nullable String displayName) {
    }

    /** 已连接实例（contract {@code Instance}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeInstance(String id, @Nullable String name, @Nullable String domain,
                             @Nullable String ujcmsVersion, @Nullable String lastSeenAt) {
    }

    /** 授权条目（contract {@code Entitlement}；item 为条目 slug，仅 ACTIVE 可见，D-K6） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entitlement(String item, @Nullable String name, @Nullable String source,
                              @Nullable String grantedAt) {
    }

    /** 授权分页（contract {@code EntitlementPage}） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntitlementPage(int page, int pageSize, long total, int totalPages,
                                  List<Entitlement> items) {
    }

    /** 下载授权（contract {@code DownloadGrant}；url 为签名短时效相对引用，sha256 强制，D-K7） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadGrant(String url, String sha256, long size, @Nullable String expiresAt) {
    }

    /** 已下载的包：临时文件 + 边写边算的 sha256 + 字节数（成功后由调用方清理临时文件） */
    public record DownloadedPackage(Path file, String sha256, long size) {
    }

    /** 目录资产字节 + 透传头（资产代理响应用，D-C1-4） */
    public record Asset(byte[] bytes, @Nullable String contentType, @Nullable String cacheControl) {
    }
}
