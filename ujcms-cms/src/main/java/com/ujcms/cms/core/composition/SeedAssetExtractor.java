package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.web.PathResolver;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 反向资源遍历器（theme-seed-export §5）——{@link SeedAssetCopier} 的镜像。对导出字段中的 URL：
 * <ol>
 *   <li><b>分类</b>（§5.2）：本站上传存储前缀 → 复制字节并改写为 {@code {{asset:<relpath>}}}；
 *       主题目录前缀（{@code <templateStorageUrl>/<siteId>/<theme>}）→ 改写为 {@code {{theme}}}
 *       （随渲染器走，不复制字节）；外链 {@code http(s)} → 原样保留并计入计划的 {@code externalUrls}；
 *       他站点附件 → 告警原样保留；其余站内相对路径（页面链接等）→ 原样。</li>
 *   <li><b>字节与相对路径稳定性</b>（§5.3）：URL → {@code Attachment.path}（按 URL 查行，
 *       回退前缀剥离），经上传 {@link FileHandler} 读取。存储路径已是
 *       {@code theme-seed/<siteId>/<theme>/<relpath>}（先前导入的产物）则<b>保留原 relpath</b>——
 *       往返稳定；否则 relpath = {@code img|media/<内容散列8位>-<原文件名>}，相同字节去重为一个文件。</li>
 *   <li><b>缺文件不失败</b>（§5.4）：物理文件缺失时告警并保留原 URL——导入侧「单张缺图不失败」的镜像。</li>
 * </ol>
 * 全程只读（D2）：不写上传存储、不登记附件。
 *
 * @author PONY
 */
@Component
public class SeedAssetExtractor {
    private static final Logger logger = LoggerFactory.getLogger(SeedAssetExtractor.class);

    private static final Pattern EXTERNAL_URL = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    /**
     * 富文本内嵌外链（src/href 属性）——计划 externalUrls 口径；正文纯文本里的链接不计
     */
    private static final Pattern INLINE_EXTERNAL = Pattern.compile(
            "(?:src|href)\\s*=\\s*[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    /**
     * 图片扩展名（relpath 铸造的 img/ 目录归类；其余归 media/）
     */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "svg", "png", "jpg", "jpeg", "gif", "webp", "avif", "bmp", "ico");

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final AttachmentService attachmentService;

    public SeedAssetExtractor(ConfigService configService, PathResolver pathResolver,
                              AttachmentService attachmentService) {
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.attachmentService = attachmentService;
    }

    /**
     * 开启一次导出会话（同一 URL 本次运行只分类/复制一次）。
     *
     * @param siteId    站点ID
     * @param themeName 主题名（目录名）
     */
    public Session open(Long siteId, String themeName) {
        FileHandler fileHandler = configService.getUnique().getUploadStorage().getFileHandler(pathResolver);
        String templateStoreUrl = configService.getUnique().getTemplateStorage().getUrl();
        return new Session(siteId, themeName, fileHandler, templateStoreUrl);
    }

    /**
     * 单次导出的资源改写会话
     */
    public class Session {
        private final Long siteId;
        private final String themeName;
        private final FileHandler fileHandler;
        /**
         * 上传存储 URL 前缀（分类 (a)：本站上传 → 导出）
         */
        private final String uploadPrefix;
        /**
         * 主题目录 URL 前缀 {@code <templateStorageUrl>/<siteId>/<theme>}（分类 (b) → {@code {{theme}}}）
         */
        private final String themePrefix;
        /**
         * 会话内 URL → 改写结果缓存（同 URL 恒同结果；诊断只记一次）
         */
        private final Map<String, String> rewriteByUrl = new HashMap<>(32);
        /**
         * 存储路径 → relpath（同文件去重）
         */
        private final Map<String, String> relpathByPathname = new HashMap<>(16);
        /**
         * relpath → 字节（进入 {@code content/assets/} 的文件）
         */
        private final TreeMap<String, byte[]> assets = new TreeMap<>();
        private final TreeSet<String> externalUrls = new TreeSet<>();
        private final List<ExportPlan.RefIssue> issues = new ArrayList<>();
        /**
         * 内嵌 URL 识别：主题前缀或上传前缀起始、遇定界符止
         */
        private final Pattern inlineUrl;

        private Session(Long siteId, String themeName, FileHandler fileHandler, String templateStoreUrl) {
            this.siteId = siteId;
            this.themeName = themeName;
            this.fileHandler = fileHandler;
            this.uploadPrefix = stripTrailingSlash(fileHandler.getDisplayPrefix());
            this.themePrefix = stripTrailingSlash(templateStoreUrl) + "/" + siteId + "/" + themeName;
            this.inlineUrl = Pattern.compile("(?:" + Pattern.quote(themePrefix) + "|"
                    + Pattern.quote(uploadPrefix) + ")[^\\s\"'<>()\\[\\]{}\\\\]*");
        }

        /**
         * 整串 URL 字段的分类改写（题图/视频/下载/媒体对象 url 等：值本身即 URL）。
         */
        @Nullable
        public String rewriteUrl(@Nullable String url, String where) {
            if (url == null || url.isEmpty()) {
                return url;
            }
            String cached = rewriteByUrl.get(url);
            if (cached != null) {
                return cached;
            }
            String rewritten = classify(url, where);
            rewriteByUrl.put(url, rewritten);
            return rewritten;
        }

        /**
         * 富文本/Markdown 的内嵌改写：识别主题/上传前缀起始的 URL 子串逐一分类改写；
         * src/href 外链计入 {@code externalUrls}。
         */
        @Nullable
        public String rewriteInline(@Nullable String text, String where) {
            if (text == null || text.isEmpty()) {
                return text;
            }
            Matcher external = INLINE_EXTERNAL.matcher(text);
            while (external.find()) {
                externalUrls.add(external.group(1));
            }
            if (!text.contains(themePrefix) && !text.contains(uploadPrefix + "/")) {
                return text;
            }
            Matcher matcher = inlineUrl.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(
                        String.valueOf(rewriteUrl(matcher.group(), where))));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        private String classify(String url, String where) {
            // 已是令牌（防御：正常行内不应出现——导入即展开）：原样
            if (url.contains("{{")) {
                return url;
            }
            if (url.equals(themePrefix) || url.startsWith(themePrefix + "/")) {
                // 主题目录资源随渲染器走（{{theme}}），不复制字节
                return "{{theme}}" + url.substring(themePrefix.length());
            }
            if (url.startsWith(uploadPrefix + "/")) {
                return exportUpload(url, where);
            }
            if (EXTERNAL_URL.matcher(url).matches()) {
                externalUrls.add(url);
                return url;
            }
            // 其余站内相对路径（页面链接等）：原样保留
            return url;
        }

        /**
         * 本站上传存储 URL → 复制字节到 {@code content/assets/<relpath>} 并改写为
         * {@code {{asset:<relpath>}}}。他站点附件与缺失文件原样保留（§5.2(d) / §5.4）。
         */
        private String exportUpload(String url, String where) {
            Attachment attachment = attachmentService.findByUrl(url);
            if (attachment != null && !siteId.equals(attachment.getSiteId())) {
                issues.add(new ExportPlan.RefIssue("asset", url,
                        "他站点附件（siteId=" + attachment.getSiteId() + "），原样保留：" + where));
                logger.warn("Export asset belongs to another site, left verbatim: {} ({})", url, where);
                return url;
            }
            String pathname = attachment != null ? attachment.getPath() : url.substring(uploadPrefix.length());
            String existing = relpathByPathname.get(pathname);
            if (existing != null) {
                return "{{asset:" + existing + "}}";
            }
            byte[] bytes = readBytes(pathname);
            if (bytes == null) {
                issues.add(new ExportPlan.RefIssue("asset", url, "物理文件缺失，原样保留：" + where));
                logger.warn("Export asset file missing, URL left verbatim: {} ({})", url, where);
                return url;
            }
            String relpath = mintRelpath(pathname, bytes);
            relpathByPathname.put(pathname, relpath);
            assets.putIfAbsent(relpath, bytes);
            return "{{asset:" + relpath + "}}";
        }

        /**
         * relpath 稳定性（§5.3）：先前导入的确定性路径保留原 relpath（往返稳定）；
         * 否则以内容散列 + 原文件名铸造（相同字节去重）。
         */
        private String mintRelpath(String pathname, byte[] bytes) {
            String seedPrefix = SeedAssetCopier.THEME_SEED_DIR + siteId + "/" + themeName + "/";
            if (pathname.startsWith(seedPrefix)) {
                return pathname.substring(seedPrefix.length());
            }
            String basename = sanitizeBasename(pathname.substring(pathname.lastIndexOf('/') + 1));
            String extension = basename.contains(".")
                    ? basename.substring(basename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
            String dir = IMAGE_EXTENSIONS.contains(extension) ? "img" : "media";
            return dir + "/" + sha256Hex8(bytes) + "-" + basename;
        }

        @Nullable
        private byte[] readBytes(String pathname) {
            try {
                File file = fileHandler.getFile(pathname);
                if (file == null || !file.isFile()) {
                    return null;
                }
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                logger.warn("Export asset read failed: {}", pathname, e);
                return null;
            }
        }

        /**
         * 进入 {@code content/assets/} 的文件（relpath → 字节，字典序）
         */
        public TreeMap<String, byte[]> assets() {
            return assets;
        }

        /**
         * 资源总字节数
         */
        public long assetBytes() {
            return assets.values().stream().mapToLong(bytes -> bytes.length).sum();
        }

        /**
         * 最大的资源文件（字节数降序，至多 {@code limit} 个；§7.3 超限排查）
         */
        public List<ExportPlan.AssetFile> largestAssets(int limit) {
            return assets.entrySet().stream()
                    .map(entry -> new ExportPlan.AssetFile(entry.getKey(), entry.getValue().length))
                    .sorted(Comparator.comparingLong(ExportPlan.AssetFile::bytes).reversed()
                            .thenComparing(ExportPlan.AssetFile::relpath))
                    .limit(limit)
                    .toList();
        }

        /**
         * 外链 URL（去重字典序）
         */
        public List<String> externalUrls() {
            return List.copyOf(externalUrls);
        }

        /**
         * 资源类诊断（他站点附件、缺失文件）
         */
        public List<ExportPlan.RefIssue> issues() {
            return issues;
        }
    }

    private static String sanitizeBasename(String basename) {
        String safe = basename.replaceAll("[\\\\:]", "-");
        while (safe.contains("..")) {
            safe = safe.replace("..", ".");
        }
        return safe.isEmpty() ? "file" : safe;
    }

    private static String sha256Hex8(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16))
                        .append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
