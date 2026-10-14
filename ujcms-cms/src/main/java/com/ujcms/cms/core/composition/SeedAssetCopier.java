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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容种子资源复制器（theme-marketplace §5.5 / D6）：展开 {@code {{asset:<relpath>}}} 令牌——
 * 把主题包 {@code seed/content/assets/<relpath>} 复制进上传存储的<b>确定性路径</b>
 * {@code /theme-seed/<siteId>/<themeName>/<relpath>}（重复导入原地覆盖，不累积副本），
 * 令牌改写为公开 URL，并登记 {@link Attachment} 行（按 URL 幂等），使服务层保存时的
 * {@code AttachmentRefer} 记账照常生效。
 *
 * <p>内容资源刻意<b>不</b>留在主题目录（{@code {{theme}}} 前缀）——主题卸载会删除主题目录，
 * 而内容行在 KEEP 策略下要活得比主题久。路径含站点ID：主题是站点级副本（D3），
 * 两个站点安装同名主题的资源互不覆盖。
 *
 * <p>缺失的资源文件告警后仍展开为确定性 URL（作者补传即可修复）——单张缺图不使整个实体导入失败。
 *
 * @author PONY
 */
@Component
public class SeedAssetCopier {
    private static final Logger logger = LoggerFactory.getLogger(SeedAssetCopier.class);

    private static final Pattern ASSET_TOKEN = Pattern.compile("\\{\\{asset:([^}]+)}}");
    /**
     * 上传存储内的内容种子资源根目录
     */
    public static final String THEME_SEED_DIR = "/theme-seed/";

    private final ConfigService configService;
    private final PathResolver pathResolver;
    private final AttachmentService attachmentService;

    public SeedAssetCopier(ConfigService configService, PathResolver pathResolver,
                           AttachmentService attachmentService) {
        this.configService = configService;
        this.pathResolver = pathResolver;
        this.attachmentService = attachmentService;
    }

    /**
     * 开启一次导入会话（同一 relpath 本次运行只复制一次）。
     *
     * @param siteId    站点ID
     * @param themeName 主题名
     * @param assetsDir 主题包资源目录（{@code seed/content/assets}），可不存在
     * @param userId    附件归属用户ID
     */
    public Session open(Long siteId, String themeName, File assetsDir, Long userId) {
        FileHandler fileHandler = configService.getUnique().getUploadStorage().getFileHandler(pathResolver);
        return new Session(siteId, themeName, assetsDir, userId, fileHandler, false);
    }

    /**
     * 开启一次<b>干跑</b>会话（seed/preview 用，§6.2）：令牌照常展开为确定性 URL，
     * 但不复制文件、不登记附件——只读，供投影散列对比。
     */
    public Session openDry(Long siteId, String themeName) {
        FileHandler fileHandler = configService.getUnique().getUploadStorage().getFileHandler(pathResolver);
        return new Session(siteId, themeName, new File(""), 0L, fileHandler, true);
    }

    /**
     * 单次导入的资源展开会话
     */
    public class Session {
        private final Long siteId;
        private final String themeName;
        private final File assetsDir;
        private final Long userId;
        private final FileHandler fileHandler;
        /**
         * 干跑（seed/preview）：只展开 URL，不复制、不登记附件
         */
        private final boolean dry;
        /**
         * relpath → 公开URL（本次运行内每个资源只复制一次）
         */
        private final Map<String, String> urlByRelpath = new HashMap<>(16);
        private int copied = 0;

        private Session(Long siteId, String themeName, File assetsDir, Long userId, FileHandler fileHandler,
                        boolean dry) {
            this.siteId = siteId;
            this.themeName = themeName;
            this.assetsDir = assetsDir;
            this.userId = userId;
            this.fileHandler = fileHandler;
            this.dry = dry;
        }

        /**
         * 展开字符串中的全部 {@code {{asset:…}}} 令牌（正文 HTML 可含多个）。
         */
        @Nullable
        public String expand(@Nullable String value) {
            if (value == null || !value.contains("{{asset:")) {
                return value;
            }
            Matcher matcher = ASSET_TOKEN.matcher(value);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(urlFor(matcher.group(1).trim())));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        /**
         * 递归展开映射（customs）内全部字符串值的资源令牌。
         */
        @Nullable
        public Map<String, Object> expandMap(@Nullable Map<String, Object> map) {
            if (map == null) {
                return null;
            }
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> out.put(k, expandValue(v)));
            return out;
        }

        @Nullable
        private Object expandValue(@Nullable Object value) {
            if (value instanceof String str) {
                return expand(str);
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>(map.size());
                map.forEach((k, v) -> out.put(String.valueOf(k), expandValue(v)));
                return out;
            }
            if (value instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                list.forEach(item -> out.add(expandValue(item)));
                return out;
            }
            return value;
        }

        /**
         * 本次运行复制的资源文件数
         */
        public int getCopied() {
            return copied;
        }

        private String urlFor(String relpath) {
            String cached = urlByRelpath.get(relpath);
            if (cached != null) {
                return cached;
            }
            String pathname = THEME_SEED_DIR + siteId + "/" + themeName + "/" + relpath;
            String url = fileHandler.getDisplayPrefix() + pathname;
            if (isUnsafe(relpath)) {
                // 路径穿越防御：令牌来自主题包 JSON，路径可被作者任意书写
                logger.warn("Seed asset relpath rejected (unsafe): {}", relpath);
                return "{{asset:" + relpath + "}}";
            }
            if (dry) {
                // 干跑（seed/preview）：URL 确定性可推导，无需落盘即可对比投影散列
                urlByRelpath.put(relpath, url);
                return url;
            }
            File source = new File(assetsDir, relpath);
            if (source.isFile()) {
                fileHandler.store(pathname, source);
                registerAttachment(relpath, pathname, url, source.length());
                copied++;
            } else {
                logger.warn("Seed asset file missing, token expanded anyway: {} (theme={})", relpath, themeName);
            }
            urlByRelpath.put(relpath, url);
            return url;
        }

        /**
         * 登记附件行（按 URL 幂等——重复导入不产生重复附件），使内容行保存时的
         * {@code AttachmentRefer} 记账生效、附件被标记为已使用而免于清理。
         */
        private void registerAttachment(String relpath, String pathname, String url, long length) {
            if (attachmentService.findByUrl(url) == null) {
                String name = relpath.substring(relpath.lastIndexOf('/') + 1);
                attachmentService.insert(new Attachment(siteId, userId, name, pathname, url, length));
            }
        }

        private boolean isUnsafe(String relpath) {
            return relpath.isEmpty() || relpath.contains("..") || relpath.startsWith("/")
                    || relpath.contains("\\") || relpath.contains(":");
        }
    }
}
