package com.ujcms.commons.db.tree;

import org.springframework.lang.Nullable;

import java.util.*;

import static com.ujcms.commons.db.tree.TreeMoveType.BEFORE;
import static com.ujcms.commons.db.tree.TreeMoveType.INNER;

/**
 * 树形结构 Service
 *
 * @author PONY
 */
public class TreeService<T extends TreeEntity> {
    private final TreeEntityMapper<T> entityMapper;
    private final TreeRelationMapper relationMapper;

    public TreeService(TreeEntityMapper<T> entityMapper, TreeRelationMapper relationMapper) {
        this.entityMapper = entityMapper;
        this.relationMapper = relationMapper;
    }

    public void insert(T bean) {
        insert(bean, null);
    }

    public void insert(T bean, @Nullable Long siteId) {
        Long parentId = bean.getParentId();
        if (parentId != null) {
            T parent = entityMapper.select(parentId);
            bean.setDepth((short) ((parent != null ? parent.getDepth() : 0) + 1));
        }
        bean.setOrder(Optional.ofNullable(entityMapper.maxOrder(parentId, siteId)).orElse(0) + 1);
        // 先增加order，空出位置，再插入数据
        entityMapper.updateOrder(bean.getOrder(), Integer.MAX_VALUE, 1, siteId);
        entityMapper.insert(bean);
        // 处理树结构关系
        relationMapper.save(bean.getId(), bean.getId());
        if (parentId != null) {
            relationMapper.add(bean.getId(), parentId);
        }
    }

    public void move(T from, T to, TreeMoveType type) {
        move(from, to, type, null);
    }

    public void move(T from, T to, TreeMoveType type, @Nullable Long siteId) {
        // 移动order。所有类型都需要的操作
        moveOrder(from, to, siteId, type);
        // 同级移动。无需后续处理
        if (type != INNER && Objects.equals(from.getParentId(), to.getParentId())) {
            return;
        }
        // 移除源节点与上级的闭包关系。要在order之后
        relationMapper.move(from.getId());
        // 上级节点及深度
        Long parentId = to.getParentId();
        short depthDiff = (short) (to.getDepth() - from.getDepth());
        if (type == INNER) {
            parentId = to.getId();
            depthDiff = (short) (to.getDepth() + 1 - from.getDepth());
        }
        // 增加源节点与上级的闭包关系
        if (parentId != null) {
            relationMapper.append(from.getId(), parentId);
        }
        // 处理depth
        entityMapper.updateDepth(from.getId(), depthDiff);
        // 处理parentId
        entityMapper.updateParentId(from.getId(), parentId);
    }

    private void moveOrder(T from, T to, @Nullable Long siteId, TreeMoveType type) {
        int fromOrder = from.getOrder();
        int toOrder = to.getOrder();
        boolean moveUp = fromOrder > toOrder;
        // 源节点（及其子节点）的最大order值
        int fromMaxOrder = Optional.ofNullable(entityMapper.maxOrder(from.getId(), siteId)).orElse(0);
        // 目标节点（及其子节点）的最大order值
        int toMaxOrder = Optional.ofNullable(entityMapper.maxOrder(to.getId(), siteId)).orElse(0);
        // 源节点（及其子节点）的总数量
        int fromCount = fromMaxOrder - fromOrder + 1;
        // 增加目标节点之上或之下的空间
        if (type == BEFORE) {
            entityMapper.updateOrder(toOrder, Integer.MAX_VALUE, fromCount, siteId);
            toOrder += fromCount;
            toMaxOrder += fromCount;
        } else {
            entityMapper.updateOrder(toMaxOrder + 1, Integer.MAX_VALUE, fromCount, siteId);
        }
        if (moveUp) {
            fromOrder += fromCount;
            fromMaxOrder += fromCount;
        }
        // 移动源节点
        int moveNum = toMaxOrder - fromOrder + 1;
        if (type == BEFORE) {
            moveNum = toOrder - fromOrder - fromCount;
        }
        entityMapper.updateOrder(fromOrder, fromMaxOrder, moveNum, siteId);
        // 减掉源节点空出来的空间
        entityMapper.updateOrder(fromMaxOrder + 1, Integer.MAX_VALUE, -fromCount, siteId);
    }

    /**
     * 整理树形结构
     *
     * @param tree 数据列表
     */
    public void tidyTreeOrderAndDepth(List<TreeSortEntity> tree, int size) {
        List<TreeEntity> toBeUpdated = new ArrayList<>(size);
        resetOrderData(toBeUpdated, tree, 1, 1);
        int batchSize = 1000;
        for (int i = 0, len = toBeUpdated.size(); i < len; i += batchSize) {
            int end = i + batchSize;
            if (end > len) {
                end = len;
            }
            entityMapper.batchUpdateOrderAndDepth(toBeUpdated.subList(i, end));
        }
    }

    public void tidyTreeRelation(List<TreeSortEntity> tree, int size) {
        List<TreeRelationEntity> toBeSaved = new ArrayList<>(size * 3);
        resetRelationData(toBeSaved, tree);
        int batchSize = 1000;
        for (int i = 0, len = toBeSaved.size(); i < len; i += batchSize) {
            int end = i + batchSize;
            if (end > len) {
                end = len;
            }
            relationMapper.saveBatch(toBeSaved.subList(i, end));
        }
    }

    private void resetRelationData(List<TreeRelationEntity> toBeSaved, List<TreeSortEntity> list) {
        for (TreeSortEntity bean : list) {
            bean.addToRelations(toBeSaved);
            resetRelationData(toBeSaved, bean.getChildren());
        }
    }

    private int resetOrderData(List<TreeEntity> toBeUpdated, List<TreeSortEntity> list, int index, int depth) {
        for (TreeSortEntity item : list) {
            TreeEntity entity = item.getEntity();
            entity.setOrder(index);
            entity.setDepth((short) depth);
            toBeUpdated.add(entity);
            index += 1;
            index = resetOrderData(toBeUpdated, item.getChildren(), index, depth + 1);
        }
        return index;
    }

    public List<TreeSortEntity> toTree(List<T> list) {
        List<TreeSortEntity> root = new ArrayList<>();
        List<TreeSortEntity> beans = new ArrayList<>(list.size());
        Map<Long, TreeSortEntity> tempMap = new HashMap<>((int) (list.size() / 0.75 + 1));
        for (T item : list) {
            TreeSortEntity bean = new TreeSortEntity(item);
            tempMap.put(item.getId(), bean);
            beans.add(bean);
        }
        for (TreeSortEntity item : beans) {
            TreeSortEntity parent = tempMap.get(item.getEntity().getParentId());
            if (parent != null) {
                parent.getChildren().add(item);
            } else {
                root.add(item);
            }
        }
        return root;
    }

    public int delete(Long id, Integer order) {
        return delete(id, order, null);
    }

    public int delete(Long id, Integer order, @Nullable Long siteId) {
        relationMapper.deleteByAncestorId(id);
        // 后面的节点序号全部 -1
        entityMapper.updateOrder(order, Integer.MAX_VALUE, -1, siteId);
        return entityMapper.delete(id);
    }
}
