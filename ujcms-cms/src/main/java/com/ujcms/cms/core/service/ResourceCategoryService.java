package com.ujcms.cms.core.service;

import com.ujcms.cms.core.domain.ResourceCategory;
import com.ujcms.cms.core.listener.SiteDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.AttachmentFavoriteMapper;
import com.ujcms.cms.core.mapper.ResourceCategoryMapper;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源库分类 Service
 *
 * @author PONY
 */
@Service
public class ResourceCategoryService implements SiteDeleteListener, UserDeleteListener {
    private final ResourceCategoryMapper mapper;
    private final AttachmentFavoriteMapper favoriteMapper;
    private final SnowflakeSequence snowflakeSequence;

    public ResourceCategoryService(ResourceCategoryMapper mapper, AttachmentFavoriteMapper favoriteMapper,
                                   SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.favoriteMapper = favoriteMapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ResourceCategory bean) {
        bean.setId(snowflakeSequence.nextId());
        short depth = 1;
        Long parentId = bean.getParentId();
        if (parentId != null) {
            ResourceCategory parent = mapper.select(parentId);
            if (parent != null) {
                depth = (short) (parent.getDepth() + 1);
            }
        }
        bean.setDepth(depth);
        Integer maxOrder = mapper.maxOrder(bean.getSiteId(), parentId);
        bean.setOrder((maxOrder == null ? 0 : maxOrder) + 1);
        bean.setCreated(OffsetDateTime.now());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ResourceCategory bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Nullable
    public ResourceCategory select(Long id) {
        return mapper.select(id);
    }

    public int countChildren(Long parentId) {
        return mapper.countChildren(parentId);
    }

    public int countAttachments(Long categoryId) {
        return mapper.countAttachments(categoryId);
    }

    /**
     * 统计某个我的资源库分类下的收藏数量（用于删除个人分类前的非空校验）。
     */
    public int countFavorites(Long categoryId) {
        return favoriteMapper.countByCategory(categoryId);
    }

    /**
     * 获取站点资源库分类树（嵌套结构，根节点列表）。
     * {@code ownerId} 为空获取共享分类树，否则获取该用户的个人分类树。
     */
    public List<ResourceCategory> selectTree(Long siteId, @Nullable Long ownerId) {
        List<ResourceCategory> all = mapper.listBySiteId(siteId, ownerId);
        Map<Long, ResourceCategory> byId = new HashMap<>(all.size() * 2);
        all.forEach(c -> byId.put(c.getId(), c));
        List<ResourceCategory> roots = new ArrayList<>();
        for (ResourceCategory c : all) {
            Long parentId = c.getParentId();
            ResourceCategory parent = parentId != null ? byId.get(parentId) : null;
            if (parent != null) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(c);
            } else {
                roots.add(c);
            }
        }
        return roots;
    }

    /**
     * 查找或创建站点的“未分类”顶级分类。共享但未归类的附件归入此分类。
     */
    @Transactional(rollbackFor = Exception.class)
    public ResourceCategory getOrCreateUncategorized(Long siteId) {
        List<ResourceCategory> found = mapper.findByName(siteId, null, ResourceCategory.UNCATEGORIZED);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        ResourceCategory bean = new ResourceCategory();
        bean.setSiteId(siteId);
        bean.setName(ResourceCategory.UNCATEGORIZED);
        insert(bean);
        return bean;
    }

    @Override
    public void preSiteDelete(Long siteId) {
        mapper.deleteBySiteId(siteId);
    }

    /**
     * 用户删除：清除其个人分类（我的资源库分组）。收藏记录由 {@code AttachmentService#preUserDelete} 清除。
     */
    @Override
    public void preUserDelete(Long userId) {
        mapper.deleteByOwnerId(userId);
    }

    @Override
    public int deleteListenerOrder() {
        return 100;
    }
}
