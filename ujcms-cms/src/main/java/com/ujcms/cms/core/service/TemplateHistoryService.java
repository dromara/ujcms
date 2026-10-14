package com.ujcms.cms.core.service;

import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.TemplateHistory;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.mapper.TemplateHistoryMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.FilesEx;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.io.FilenameUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 模板编辑历史服务（docs/spec/template-history/plan.md，design-review-2026-07 P3-6 / T6）。
 * 区域历史（region-history）的文件版：只追加、回填漂移、追加去重、每路径保留上限裁剪、
 * 恢复 = 经 {@link FileHandler#store(String, String)} 的一次标准写入（历史只增不减，已删除文件凭历史重建）。
 *
 * <p>捕获范围：文本文件（编辑器 {@link WebFile#TEXT_EXTENSIONS}，自 template-manager P3 后含
 * json——构成清单可就地编辑）、单版本 ≤{@link #MAX_CONTENT_BYTES}、路径 ≤{@link #MAX_PATH_LENGTH}
 * 字符。超限跳过并 WARN——多 MB 的主题资产不是手改工件，正是要排除的体量。
 *
 * <p>时序语义：文件写入不可回滚，历史追加在<b>文件写入成功之后</b>、独立事务内——追加失败对操作员
 * 报 500（文件已写入），缺口由下次保存的回填补上；先记后写会留下从未落盘的幽灵条目，弃。
 * 读写仅经 {@link FileHandler} 可移植原语（getWebFile / writeOutputStream / store / listFiles），
 * 本地与远端对象存储行为一致。
 *
 * @author PONY
 */
@Service
public class TemplateHistoryService implements SiteDeleteListener {
    private static final Logger logger = LoggerFactory.getLogger(TemplateHistoryService.class);

    /**
     * 每路径保留上限（region-history 同值同理：失败代价不对称——太小丢掉好状态，太大多占 KB 级行）
     */
    public static final int HISTORY_RETENTION = 50;
    /**
     * 单版本内容上限（UTF-8 字节）
     */
    public static final int MAX_CONTENT_BYTES = 1024 * 1024;
    /**
     * 路径长度上限（列 VARCHAR(600) = 3×，兼容按字节计数的国产库）
     */
    public static final int MAX_PATH_LENGTH = 200;
    /**
     * 目录子树历史列表上限
     */
    public static final int DIR_LIST_LIMIT = 100;
    /**
     * 删除目录时的递归捕获上限（整主题删除是生命周期操作，不是编辑）
     */
    private static final int DELETE_WALK_LIMIT = 500;
    /**
     * 递归捕获跳过的开发/构建产物目录（与 SeedPackageWriter.EXCLUDED_DIRS 同清单）：
     * 非手改工件、可由源码重建，且其中的文本文件（node_modules 的 .js 等）会吞掉
     * {@link #DELETE_WALK_LIMIT} 预算、把真正值得保留的操作员内容挤出捕获
     */
    private static final Set<String> WALK_EXCLUDED_DIRS = Set.of("node_modules", "_src", ".git", ".idea", ".vscode");

    /**
     * 可捕获扩展名：与编辑器文本扩展名同集（json 曾在此单独补充，template-manager P3 后已并入
     * {@link WebFile#TEXT_EXTENSIONS}——捕获面与可编辑面保持一致）
     */
    private static final Set<String> ELIGIBLE_EXTENSIONS = Set.copyOf(WebFile.TEXT_EXTENSIONS);

    private final TemplateHistoryMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public TemplateHistoryService(TemplateHistoryMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 路径是否可捕获（扩展名 + 路径长度；长度超限 WARN——静默跳过与静默丢失同罪）。
     */
    public boolean isEligiblePath(String path) {
        if (!ELIGIBLE_EXTENSIONS.contains(FilenameUtils.getExtension(path).toLowerCase())) {
            return false;
        }
        if (path.length() > MAX_PATH_LENGTH) {
            logger.warn("Template history skipped, path too long (>{}): {}", MAX_PATH_LENGTH, path);
            return false;
        }
        return true;
    }

    /**
     * 读取当前文件文本（不存在/目录/不可捕获/超限 → null；超限 WARN）。回填与恢复的前置读取。
     */
    @Nullable
    public String readEligibleText(FileHandler fileHandler, String path) {
        if (!isEligiblePath(path)) {
            return null;
        }
        WebFile webFile = fileHandler.getWebFile(path);
        if (webFile == null || webFile.isDirectory()) {
            return null;
        }
        if (webFile.getLength() > MAX_CONTENT_BYTES) {
            logger.warn("Template history skipped, file too large ({} bytes): {}", webFile.getLength(), path);
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fileHandler.writeOutputStream(path, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    /**
     * 记录一次写入（保存/上传/恢复共用）：先回填被替换状态（与最新条目不同才补记——覆盖特性上线前
     * 存量与旁路写入），再追加新状态（与最新条目相同则去重跳过——无变化保存不刷屏），最后裁剪。
     *
     * @param path         存储相对路径
     * @param preText      写入前的文件文本（{@link #readEligibleText}；null = 不存在或不可捕获）
     * @param newText      写入的新文本（null = 本次写入不可捕获——如超限上传，仅回填被毁状态）
     * @param source       来源（save / upload / restore）
     * @param actor        操作人用户名
     * @param restoredFrom 恢复来源历史ID（仅 source=restore）
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordWrite(String path, @Nullable String preText, @Nullable String newText, String source,
                            @Nullable String actor, @Nullable Long restoredFrom) {
        if (!isEligiblePath(path)) {
            return;
        }
        TemplateHistory latest = latest(path);
        if (preText != null && (latest == null || !latest.getContent().equals(preText))) {
            latest = append(path, preText, TemplateHistory.SOURCE_BACKFILL, actor, null);
        }
        if (newText != null) {
            int bytes = newText.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_CONTENT_BYTES) {
                logger.warn("Template history skipped, content too large ({} bytes): {}", bytes, path);
            } else if (latest == null || !latest.getContent().equals(newText)) {
                append(path, newText, source, actor, restoredFrom);
            }
        }
        trim(path);
    }

    /**
     * 删除前递归收集可捕获文件（文件或目录；须在删除<b>之前</b>调用——删除后内容已不可读）。
     * 超过 {@link #DELETE_WALK_LIMIT} 个文件停止收集并 WARN；{@link #WALK_EXCLUDED_DIRS}
     * 开发目录整树跳过。
     */
    public List<CapturedFile> collectForDelete(FileHandler fileHandler, String path) {
        List<CapturedFile> out = new ArrayList<>();
        collect(fileHandler, path, out);
        return out;
    }

    private void collect(FileHandler fileHandler, String path, List<CapturedFile> out) {
        if (out.size() >= DELETE_WALK_LIMIT) {
            return;
        }
        WebFile webFile = fileHandler.getWebFile(path);
        if (webFile == null) {
            return;
        }
        if (webFile.isDirectory()) {
            if (WALK_EXCLUDED_DIRS.contains(FilenameUtils.getName(path))) {
                return;
            }
            for (WebFile child : fileHandler.listFiles(path)) {
                if (out.size() >= DELETE_WALK_LIMIT) {
                    logger.warn("Template history delete-capture truncated at {} files under: {}",
                            DELETE_WALK_LIMIT, path);
                    return;
                }
                collect(fileHandler, child.getId(), out);
            }
            return;
        }
        String text = readEligibleText(fileHandler, path);
        if (text != null) {
            out.add(new CapturedFile(path, text));
        }
    }

    /**
     * 全量清空前递归收集可捕获文件（模板备份恢复的清空阶段——恢复 = 清空 + 解包重建，清空即删除类
     * 数据丢失路径）。与文件管理器多选删除同一约定：逐顶层条目走 {@link #collectForDelete}，
     * 各顶层子树独立受 {@link #DELETE_WALK_LIMIT} 约束。
     */
    public List<CapturedFile> collectForWipe(FileHandler fileHandler) {
        List<CapturedFile> out = new ArrayList<>();
        for (WebFile webFile : fileHandler.listFiles(FilesEx.SLASH)) {
            out.addAll(collectForDelete(fileHandler, webFile.getId()));
        }
        return out;
    }

    /**
     * 记录删除（删除成功之后调用）：逐文件追加 {@code delete} 条目（与最新条目相同则去重）并裁剪。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordDeletes(List<CapturedFile> files, @Nullable String actor) {
        for (CapturedFile file : files) {
            TemplateHistory latest = latest(file.path());
            if (latest == null || !latest.getContent().equals(file.content())) {
                append(file.path(), file.content(), TemplateHistory.SOURCE_DELETE, actor, null);
            }
            trim(file.path());
        }
    }

    /**
     * 恢复历史条目（template-history §7）：恢复 = 一次标准写入——先回填被替换的当前状态（若漂移），
     * 经 {@link FileHandler#store} 落盘（已删除文件就此重建，父目录自动创建），再追加
     * {@code source=restore} + {@code restored_from_} 溯源条目。文件管理器本无并发控制，
     * 恢复同为 last-write-wins；~5s 模板缓存延迟内生效，静态页须重新生成才可见。
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(FileHandler fileHandler, TemplateHistory entry, @Nullable String actor) {
        String path = entry.getPath();
        if (!isEligiblePath(path)) {
            throw new Http400Exception("历史条目路径不可恢复: " + path);
        }
        String pre = readEligibleText(fileHandler, path);
        fileHandler.store(path, entry.getContent());
        recordWrite(path, pre, entry.getContent(), TemplateHistory.SOURCE_RESTORE, actor, entry.getId());
    }

    /**
     * 单条历史（含内容）。
     */
    public TemplateHistory get(Long id) {
        TemplateHistory entry = mapper.select(id);
        if (entry == null) {
            throw new Http400Exception("历史条目不存在: " + id);
        }
        return entry;
    }

    /**
     * 路径下的历史（新→旧，不含内容；行数受保留上限约束）。
     */
    public List<TemplateHistory> listByPath(String path) {
        return mapper.listByPath(path);
    }

    /**
     * 目录子树下的历史（新→旧，不含内容，前 {@link #DIR_LIST_LIMIT} 条）——已删除文件的发现面。
     */
    public List<TemplateHistory> listByDir(String dir) {
        String prefix = dir.replace("#", "##").replace("%", "#%").replace("_", "#_");
        return PageMethod.startPage(1, DIR_LIST_LIMIT, false)
                .doSelectPage(() -> mapper.listByPathPrefix(prefix));
    }

    @Nullable
    private TemplateHistory latest(String path) {
        return mapper.selectLatestByPath(path);
    }

    private TemplateHistory append(String path, String content, String source, @Nullable String actor,
                                   @Nullable Long restoredFrom) {
        TemplateHistory bean = new TemplateHistory();
        bean.setId(snowflakeSequence.nextId());
        bean.setPath(path);
        bean.setContent(content);
        bean.setLength(content.getBytes(StandardCharsets.UTF_8).length);
        bean.setSource(source);
        bean.setRestoredFrom(restoredFrom);
        bean.setActor(actor);
        bean.setCreated(OffsetDateTime.now());
        mapper.insert(bean);
        return bean;
    }

    /**
     * 按保留上限裁剪路径下的历史（新→旧保留前 {@link #HISTORY_RETENTION} 条；裁剪只削最旧，
     * 回填漂移检测依赖的最新条目不受影响）。
     */
    private void trim(String path) {
        List<TemplateHistory> list = mapper.listByPath(path);
        for (int i = HISTORY_RETENTION; i < list.size(); i++) {
            mapper.delete(list.get(i).getId());
        }
    }

    /**
     * 站点删除级联（docs/spec/site-delete-cascade/plan.md）：清除站点模板子树
     * {@code /{siteId}/%} 的全部历史。恢复端点按站点子树门禁，站点没了历史永不可达；
     * 不做删除前捕获——捕获的意义是存续站点内可恢复。
     */
    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteByPathPattern("/" + siteId + "/%");
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }

    /**
     * 删除前捕获的文件（路径 + 文本内容）
     */
    public record CapturedFile(String path, String content) {
    }
}
