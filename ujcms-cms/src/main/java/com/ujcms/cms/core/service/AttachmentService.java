package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.AttachmentRefer;
import com.ujcms.cms.core.domain.ResourceCategory;
import com.ujcms.cms.core.domain.generated.GeneratedAttachment;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.AttachmentFavoriteMapper;
import com.ujcms.cms.core.mapper.AttachmentMapper;
import com.ujcms.cms.core.mapper.AttachmentReferMapper;
import com.ujcms.cms.core.service.args.AttachmentArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Uploads;
import com.ujcms.common.web.exception.Http400Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 附件 Service
 *
 * @author PONY
 */
@Service
public class AttachmentService implements SiteDeleteListener, UserDeleteListener {
    private final AttachmentMapper mapper;
    private final AttachmentReferMapper referMapper;
    private final AttachmentFavoriteMapper favoriteMapper;
    private final ResourceCategoryService resourceCategoryService;
    private final PathResolver pathResolver;
    private final SnowflakeSequence snowflakeSequence;
    private final ContentStatEvictor contentStatEvictor;

    public AttachmentService(AttachmentMapper mapper, AttachmentReferMapper referMapper,
                             AttachmentFavoriteMapper favoriteMapper, ResourceCategoryService resourceCategoryService,
                             PathResolver pathResolver, SnowflakeSequence snowflakeSequence,
                             ContentStatEvictor contentStatEvictor) {
        this.mapper = mapper;
        this.referMapper = referMapper;
        this.favoriteMapper = favoriteMapper;
        this.resourceCategoryService = resourceCategoryService;
        this.pathResolver = pathResolver;
        this.snowflakeSequence = snowflakeSequence;
        this.contentStatEvictor = contentStatEvictor;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertRefer(String referType, Long referId, List<String> urls) {
        Set<Long> ids = doInsertRefer(referType, referId, urls);
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private Set<Long> doInsertRefer(String referType, Long referId, List<String> urls) {
        Set<Long> ids = new HashSet<>();
        urls.forEach(url -> {
            Attachment attachment = mapper.findByUrl(url);
            if (attachment != null && referMapper.select(attachment.getId(), referType, referId) == null) {
                Long id = snowflakeSequence.nextId();
                referMapper.insert(new AttachmentRefer(id, attachment.getId(), referType, referId));
                ids.add(attachment.getId());
            }
        });
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRefer(String referType, Long referId, List<String> urls) {
        Set<Long> ids = findAttachmentIds(referType, referId);
        doDeleteRefer(referType, referId);
        if (!urls.isEmpty()) {
            ids.addAll(doInsertRefer(referType, referId, urls));
        }
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private Set<Long> findAttachmentIds(String referType, Long referId) {
        Set<Long> ids = new HashSet<>();
        referMapper.listByReferTypeAndReferId(referType, referId).forEach(refer -> ids.add(refer.getAttachmentId()));
        return ids;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRefer(String referType, Long referId) {
        Set<Long> ids = findAttachmentIds(referType, referId);
        doDeleteRefer(referType, referId);
        if (!ids.isEmpty()) {
            mapper.updateUsed(ids);
        }
    }

    private void doDeleteRefer(String referType, Long referId) {
        referMapper.deleteByReferTypeAndReferId(referType, referId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(Attachment bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
        contentStatEvictor.attachment(bean.getSiteId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Attachment bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        Attachment bean = select(id);
        if (bean != null) {
            return delete(bean);
        }
        return 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Attachment bean) {
        // 已被引用或加入资源库（共享/收藏）的附件处于 pin 状态，不删除文件与记录
        if (Boolean.TRUE.equals(bean.getUsed())) {
            return 0;
        }
        FileHandler fileHandler = bean.getSite().getConfig().getUploadStorage().getFileHandler(pathResolver);
        Optional.ofNullable(fileHandler.getName(bean.getUrl())).ifPresent(pathname -> {
            fileHandler.deleteFileAndEmptyParentDir(pathname);
            // 删除缩略图，如果有的话
            fileHandler.deleteFileAndEmptyParentDir(Uploads.getThumbnailName(pathname));
        });
        int count = mapper.delete(bean.getId());
        contentStatEvictor.attachment(bean.getSiteId());
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    /**
     * 删除站点下全部未使用（未被引用、未加入共享/我的资源库）的附件。
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteUnused(Long siteId) {
        int count = 0;
        for (Attachment bean : selectList(AttachmentArgs.of().siteId(siteId).used(false))) {
            count += delete(bean);
        }
        return count;
    }

    // ---------- 资源库：共享 / 收藏 / 导入 ----------

    /**
     * 加入共享资源库或移动分类。{@code categoryId} 为空时归入站点“未分类”。
     */
    @Transactional(rollbackFor = Exception.class)
    public void share(Long id, @Nullable Long categoryId) {
        Attachment bean = mapper.select(id);
        if (bean == null) {
            return;
        }
        Long catId = categoryId;
        if (catId == null) {
            catId = resourceCategoryService.getOrCreateUncategorized(bean.getSiteId()).getId();
        } else {
            ResourceCategory category = resourceCategoryService.select(catId);
            if (category == null || !category.getSiteId().equals(bean.getSiteId())) {
                throw new Http400Exception("invalid resource category for attachment: " + catId);
            }
        }
        mapper.updateSharedCategory(id, catId);
        mapper.updateUsed(Set.of(id));
    }

    /**
     * 移出共享资源库。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unshare(Long id) {
        mapper.updateSharedCategory(id, null);
        mapper.updateUsed(Set.of(id));
    }

    /**
     * 加入我的资源库（收藏），归入“未分类”。
     */
    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long id, Long userId) {
        if (favoriteMapper.exists(id, userId) == 0) {
            favoriteMapper.insert(id, userId, null, OffsetDateTime.now());
            mapper.updateUsed(Set.of(id));
        }
    }

    /**
     * 设置/移动我的资源库收藏的所属分类（我的资源库分组）。未收藏时自动收藏。
     * {@code categoryId} 为空或 0 表示“未分类”。
     */
    @Transactional(rollbackFor = Exception.class)
    public void categorizeFavorite(Long id, Long userId, @Nullable Long categoryId) {
        Attachment bean = mapper.select(id);
        if (bean == null) {
            return;
        }
        Long catId = (categoryId == null || categoryId == 0L) ? null : categoryId;
        if (catId != null) {
            ResourceCategory category = resourceCategoryService.select(catId);
            if (category == null || !category.getSiteId().equals(bean.getSiteId())
                    || !userId.equals(category.getOwnerId())) {
                throw new Http400Exception("invalid personal category for favorite: " + catId);
            }
        }
        if (favoriteMapper.exists(id, userId) == 0) {
            favoriteMapper.insert(id, userId, catId, OffsetDateTime.now());
        } else {
            favoriteMapper.updateCategory(id, userId, catId);
        }
        mapper.updateUsed(Set.of(id));
    }

    /**
     * 移出我的资源库（取消收藏）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long id, Long userId) {
        if (favoriteMapper.delete(id, userId) > 0) {
            mapper.updateUsed(Set.of(id));
        }
    }

    /**
     * 将服务器文件（FTP / 上传管理 上传但未登记的文件）登记为附件，无字节拷贝。
     * 若该 URL 已存在附件记录，则直接返回已有记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Attachment importFromWebFile(WebFile file, Long siteId, Long userId) {
        Attachment existing = mapper.findByUrl(file.getUrl());
        if (existing != null) {
            return existing;
        }
        Attachment bean = new Attachment(siteId, userId, file.getName(), file.getId(), file.getUrl(), file.getLength());
        insert(bean);
        return bean;
    }

    // ---------- 资源库：分页查询 ----------

    public Page<Attachment> selectSharedPage(Long siteId, @Nullable Long categoryId, @Nullable String keyword,
                                             @Nullable List<String> extensions, int page, int pageSize) {
        List<String> patterns = toNamePatterns(extensions);
        String kw = toKeywordPattern(keyword);
        return PageMethod.startPage(page, pageSize)
                .doSelectPage(() -> mapper.selectShared(siteId, categoryId, kw, patterns));
    }

    public Page<Attachment> selectFavoritePage(Long siteId, Long userId, @Nullable Long categoryId,
                                               @Nullable String keyword, @Nullable List<String> extensions,
                                               int page, int pageSize) {
        List<String> patterns = toNamePatterns(extensions);
        String kw = toKeywordPattern(keyword);
        return PageMethod.startPage(page, pageSize)
                .doSelectPage(() -> mapper.selectByFavorite(siteId, userId, categoryId, kw, patterns));
    }

    /**
     * 文件名关键字转为 LIKE 模式（{@code %kw%}）。空白返回 {@code null} 表示不过滤。
     */
    @Nullable
    private static String toKeywordPattern(@Nullable String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : "%" + trimmed + "%";
    }

    /**
     * 将扩展名清单（如 {@code jpg}）转为按文件名后缀过滤的 LIKE 模式（{@code %.jpg}）。
     * 与 {@code all} 范围的 {@code AttachmentArgs.extensions} 行为一致：扩展名匹配 {@code name_} 列。
     * 模式作为绑定参数传入，避免使用各数据库不一致的 concat 语法。返回 {@code null} 表示不限类型。
     */
    @Nullable
    private static List<String> toNamePatterns(@Nullable List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return null;
        }
        List<String> patterns = new ArrayList<>(extensions.size());
        for (String ext : extensions) {
            if (ext == null) {
                continue;
            }
            String cleaned = ext.trim().toLowerCase();
            if (!cleaned.isEmpty()) {
                patterns.add("%." + cleaned);
            }
        }
        return patterns.isEmpty() ? null : patterns;
    }

    /**
     * 为列表填充当前用户的收藏标记（我的资源库）。
     */
    public void fillFavorited(Collection<Attachment> list, @Nullable Long userId) {
        if (userId == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream().map(Attachment::getId).toList();
        Set<Long> favored = new HashSet<>(favoriteMapper.listAttachmentIdsByUser(userId, ids));
        list.forEach(a -> a.setFavorited(favored.contains(a.getId())));
    }

    @Nullable
    public Attachment select(Long id) {
        return mapper.select(id);
    }

    @Nullable
    public Attachment findByUrl(String url) {
        return mapper.findByUrl(url);
    }

    /**
     * 在给定 url 集合中，返回已登记为附件的 url（用于服务器文件导入时过滤已存在的文件）。
     */
    public List<String> findExistingUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectExistingUrls(urls);
    }

    public List<Attachment> selectList(AttachmentArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedAttachment.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<Attachment> selectList(AttachmentArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<Attachment> selectPage(AttachmentArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 统计附件数量
     *
     * @param siteId  站点ID
     * @param created 创建日期
     * @return 附件数量
     */
    public int countByCreated(Long siteId, @Nullable OffsetDateTime created) {
        return mapper.countByCreated(siteId, created);
    }

    @Override
    public void preSiteDelete(Long siteId) {
        favoriteMapper.deleteBySiteId(siteId);
        referMapper.deleteBySiteId(siteId);
        mapper.deleteBySiteId(siteId);
    }

    /**
     * 用户删除：清除其收藏（我的资源库）并回收所涉附件的 pin 状态，否则这些附件会因无人能取消收藏而永远无法删除。
     */
    @Override
    public void preUserDelete(Long userId) {
        List<Long> ids = favoriteMapper.listAttachmentIdsByUserId(userId);
        if (ids.isEmpty()) {
            return;
        }
        favoriteMapper.deleteByUserId(userId);
        // 分批更新，避免 IN 列表过长
        int batch = 500;
        for (int i = 0; i < ids.size(); i += batch) {
            mapper.updateUsed(new HashSet<>(ids.subList(i, Math.min(ids.size(), i + batch))));
        }
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}