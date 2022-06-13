package com.ujcms.util.db.tree;

import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 树形结构 Service
 *
 * @author PONY
 */
public class TreeService<T extends TreeEntity, U extends TreeRelation> {
    private TreeEntityMapper<T> entityMapper;
    private TreeRelationMapper<U> relationMapper;

    public TreeService(TreeEntityMapper<T> entityMapper, TreeRelationMapper<U> relationMapper) {
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
    }

    public void insert(T bean) {
        insert(bean, null);
    }

    public void insert(T bean, @Nullable Integer siteId) {
        if (bean.getParentId() != null) {
            T parent = entityMapper.select(bean.getParentId());
            bean.setDepth((short) ((parent != null ? parent.getDepth() : 0) + 1));
        }
        bean.setOrder(Optional.ofNullable(entityMapper.maxOrder(bean.getParentId(), siteId)).orElse(0) + 1);
        // 先增加order，空出位置，再插入数据
        entityMapper.updateOrder(bean.getOrder(), Integer.MAX_VALUE, 1, siteId);
        entityMapper.insert(bean);
        // 处理树结构关系
        relationMapper.save(bean.getId(), bean.getId());
        if (bean.getParentId() != null) {
            relationMapper.add(bean.getId(), bean.getParentId());
        }
    }

    public void update(T bean, @Nullable Integer parentId) {
        update(bean, parentId, null);
    }

    public void update(T bean, @Nullable Integer parentId, @Nullable Integer siteId) {
        // 处理树结构关系
        int origDepth = 0, newDepth = 0;
        if (!Objects.equals(bean.getParentId(), parentId)) {
            if (bean.getParent() != null) {
                origDepth = bean.getParent().getDepth();
            }
            // 增
            int max = Optional.ofNullable(entityMapper.maxOrder(bean.getId(), siteId)).orElse(0);
            int count = max - bean.getOrder() + 1;
            int targetMax = Optional.ofNullable(entityMapper.maxOrder(parentId, siteId)).orElse(0);
            entityMapper.updateOrder(targetMax + 1, Integer.MAX_VALUE, count, siteId);
            if (max > targetMax) {
                // 向前移动
                int num = targetMax - bean.getOrder() - count + 1;
                // 移
                entityMapper.updateOrder(bean.getOrder() + count, max + count, num, siteId);
                // 减
                entityMapper.updateOrder(max + count + 1, Integer.MAX_VALUE, -count, siteId);
            } else {
                // 向后移动
                int num = targetMax - bean.getOrder() + 1;
                // 移
                entityMapper.updateOrder(bean.getOrder(), max, num, siteId);
                // 减
                entityMapper.updateOrder(max + 1, Integer.MAX_VALUE, -count, siteId);
            }
            // 设置 order，以免被更新回旧值
            bean.setOrder(targetMax + 1);
            // 处理闭包表。要在order之后
            relationMapper.move(bean.getId());
            if (parentId != null) {
                T newParent = entityMapper.select(parentId);
                if (newParent != null) {
                    newDepth = newParent.getDepth();
                }
                relationMapper.append(bean.getId(), parentId);
            }
            bean.setParentId(parentId);
        }
        entityMapper.update(bean);
        if (newDepth != origDepth) {
            entityMapper.updateDepth(bean.getId(), (short) (newDepth - origDepth));
        }
    }

    public void updateOrder(List<T> list) {
        int[] orders = list.stream().mapToInt(T::getOrder).sorted().toArray();
        for (int i = 0, len = list.size(); i < len; i += 1) {
            T bean = list.get(i);
            entityMapper.updateOrderByParentId(bean.getId(), orders[i] - bean.getOrder());
            bean.setOrder(orders[i]);
        }
    }

    public int delete(Integer id, Integer order) {
        return delete(id, order, null);
    }

    public int delete(Integer id, Integer order, @Nullable Integer siteId) {
        relationMapper.deleteByAncestorId(id);
        // 后面的节点序号全部 -1
        entityMapper.updateOrder(order, Integer.MAX_VALUE, -1, siteId);
        return entityMapper.delete(id);
    }
}
