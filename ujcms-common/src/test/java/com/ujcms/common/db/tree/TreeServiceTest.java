package com.ujcms.common.db.tree;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TreeService} 单元测试。两个 Mapper 用内存实现替身，语义照搬 ChannelMapper.xml / ChannelTreeMapper.xml 的 SQL
 * （按站点的先序序号 order、闭包表）；期望值由独立的“父子有序列表”模型重新计算先序得出，不复用被测算法。
 * 不变量：同一站点内 order 恰为先序遍历的 1..N，depth 为层级，闭包表恰为全部“祖先（含自身）→ 后代”对。
 *
 * @author PONY
 */
class TreeServiceTest {
    private static final long SITE = 1L;
    private static final long OTHER_SITE = 2L;

    /**
     * 样例树（站点 1），括号内为插入顺序打乱后仍应得到的先序：
     * <pre>
     * 1
     * ├─ 2
     * │  ├─ 4
     * │  │  ├─ 8
     * │  │  └─ 9
     * │  └─ 5
     * └─ 3
     *    ├─ 6
     *    └─ 7
     * 10
     * └─ 11
     * 12
     * </pre>
     * 站点 2 另有 21 → 22 两个节点，任何操作都不应触及。
     */
    private static final long[][] SAMPLE = {
            // {id, parentId（0 为根）}，按层插入而非先序插入：顺带覆盖“插到中间”的 insert
            {1, 0}, {10, 0}, {12, 0}, {2, 1}, {3, 1}, {11, 10}, {4, 2}, {5, 2}, {6, 3}, {7, 3}, {8, 4}, {9, 4},
    };

    @Test
    void insertAppendsAsLastChildAndKeepsPreOrder() {
        Db db = sample();
        Model model = sampleModel();
        assertEquals(List.of(), db.diff(model), "插入后的 order / depth / 闭包表应与模型一致");
        assertEquals(List.of(1L, 2L, 4L, 8L, 9L, 5L, 3L, 6L, 7L, 10L, 11L, 12L), db.idsByOrder(SITE));
    }

    @Test
    void moveKeepsPreOrderForEveryLegalCombination() {
        List<Long> ids = sampleModel().preOrder();
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (Long fromId : ids) {
            for (Long toId : ids) {
                for (TreeMoveType type : TreeMoveType.values()) {
                    Model model = sampleModel();
                    // 不能移动到自身或自身的后代之下（调用方负责拦截）
                    if (model.subtree(fromId).contains(toId)) {
                        continue;
                    }
                    cases++;
                    Db db = sample();
                    db.service.move(db.entities.select(fromId), db.entities.select(toId), type, SITE);
                    model.move(fromId, toId, type);
                    List<String> diff = db.diff(model);
                    if (!diff.isEmpty()) {
                        failures.add("move " + fromId + " " + type + " " + toId + " → " + diff);
                    }
                }
            }
        }
        assertTrue(cases > 300, "组合数异常：" + cases);
        assertEquals(List.of(), failures, failures.size() + " / " + cases + " 个组合破坏了树形结构");
    }

    /**
     * 评审时手工推演出的最小复现：孙节点移到祖父之内（升一级），原实现 order 全不变而 parent 已改，先序即被破坏
     */
    @Test
    void moveGrandchildIntoGrandparent() {
        Db db = sample();
        db.service.move(db.entities.select(8L), db.entities.select(2L), TreeMoveType.INNER, SITE);
        assertEquals(List.of(1L, 2L, 4L, 9L, 5L, 8L, 3L, 6L, 7L, 10L, 11L, 12L), db.idsByOrder(SITE));
    }

    /**
     * 移到自己上级之后（拖拽“减少缩进”的常规做法）
     */
    @Test
    void moveAfterOwnParent() {
        Db db = sample();
        db.service.move(db.entities.select(8L), db.entities.select(4L), TreeMoveType.AFTER, SITE);
        assertEquals(List.of(1L, 2L, 4L, 9L, 8L, 5L, 3L, 6L, 7L, 10L, 11L, 12L), db.idsByOrder(SITE));
    }

    @Test
    void consecutiveMovesStayConsistent() {
        Db db = sample();
        Model model = sampleModel();
        long[][] steps = {{8, 2}, {4, 10}, {3, 12}, {11, 1}, {2, 3}, {9, 1}, {10, 6}};
        TreeMoveType[] types = TreeMoveType.values();
        for (int i = 0; i < steps.length; i++) {
            long fromId = steps[i][0];
            long toId = steps[i][1];
            TreeMoveType type = types[i % types.length];
            if (model.subtree(fromId).contains(toId)) {
                continue;
            }
            db.service.move(db.entities.select(fromId), db.entities.select(toId), type, SITE);
            model.move(fromId, toId, type);
            assertEquals(List.of(), db.diff(model), "第 " + (i + 1) + " 步：move " + fromId + " " + type + " " + toId);
        }
    }

    @Test
    void deleteLeafCompactsOrder() {
        Db db = sample();
        Model model = sampleModel();
        Node leaf = db.entities.select(9L);
        db.service.delete(leaf.getId(), leaf.getOrder(), SITE);
        model.remove(9L);
        assertEquals(List.of(), db.diff(model));
    }

    @Test
    void tidyRestoresOrderDepthAndRelations() {
        Db db = sample();
        Model model = sampleModel();
        // 打乱 order / depth 并清空闭包表，模拟手工改库后的状态；兄弟间的相对顺序仍由 order 表达
        int shuffled = 100;
        for (Node node : db.nodes.values()) {
            if (node.siteId == SITE) {
                node.order = node.order * 3 + shuffled;
                node.depth = 9;
            }
        }
        db.relations.removeIf(pair -> db.nodes.get(pair.descendant()).siteId == SITE);

        List<Node> list = db.nodes.values().stream().filter(node -> node.siteId == SITE)
                .sorted(Comparator.comparing(Node::getOrder)).toList();
        List<TreeSortEntity> tree = db.service.toTree(list);
        db.service.tidyTreeOrderAndDepth(tree, list.size());
        db.service.tidyTreeRelation(tree, list.size());
        assertEquals(List.of(), db.diff(model));
    }

    // ---------------------------------------------------------------- 样例与模型

    private static Db sample() {
        Db db = new Db();
        for (long[] row : SAMPLE) {
            db.service.insert(new Node(row[0], row[1] == 0 ? null : row[1], SITE), SITE);
        }
        db.service.insert(new Node(21L, null, OTHER_SITE), OTHER_SITE);
        db.service.insert(new Node(22L, 21L, OTHER_SITE), OTHER_SITE);
        return db;
    }

    private static Model sampleModel() {
        Model model = new Model();
        for (long[] row : SAMPLE) {
            model.add(row[0], row[1] == 0 ? null : row[1]);
        }
        return model;
    }

    /**
     * 期望值模型：每个节点一个有序的子节点列表，根节点列表记在键 0 下
     */
    private static final class Model {
        private static final long ROOT = 0L;
        private final Map<Long, List<Long>> children = new LinkedHashMap<>();
        private final Map<Long, Long> parents = new LinkedHashMap<>();

        Model() {
            children.put(ROOT, new ArrayList<>());
        }

        void add(Long id, @Nullable Long parentId) {
            long parent = parentId == null ? ROOT : parentId;
            children.get(parent).add(id);
            children.put(id, new ArrayList<>());
            parents.put(id, parent);
        }

        void move(Long fromId, Long toId, TreeMoveType type) {
            children.get(parents.get(fromId)).remove(fromId);
            long parent = type == TreeMoveType.INNER ? toId : parents.get(toId);
            List<Long> siblings = children.get(parent);
            switch (type) {
                case INNER -> siblings.add(fromId);
                case BEFORE -> siblings.add(siblings.indexOf(toId), fromId);
                case AFTER -> siblings.add(siblings.indexOf(toId) + 1, fromId);
                default -> throw new IllegalStateException();
            }
            parents.put(fromId, parent);
        }

        void remove(Long id) {
            children.get(parents.remove(id)).remove(id);
            children.remove(id);
        }

        List<Long> preOrder() {
            List<Long> out = new ArrayList<>();
            walk(ROOT, out);
            return out;
        }

        private void walk(Long id, List<Long> out) {
            for (Long child : children.get(id)) {
                out.add(child);
                walk(child, out);
            }
        }

        Set<Long> subtree(Long id) {
            List<Long> out = new ArrayList<>();
            out.add(id);
            walk(id, out);
            return new HashSet<>(out);
        }

        @Nullable
        Long parentOf(Long id) {
            long parent = parents.get(id);
            return parent == ROOT ? null : parent;
        }

        int depthOf(Long id) {
            int depth = 1;
            for (Long parent = parentOf(id); parent != null; parent = parentOf(parent)) {
                depth++;
            }
            return depth;
        }
    }

    // ---------------------------------------------------------------- 内存替身

    private static final class Node implements TreeEntity {
        private Long id;
        @Nullable
        private Long parentId;
        private final long siteId;
        private Short depth = 1;
        private Integer order = 999999;

        Node(Long id, @Nullable Long parentId, long siteId) {
            this.id = id;
            this.parentId = parentId;
            this.siteId = siteId;
        }

        Node copy() {
            Node node = new Node(id, parentId, siteId);
            node.depth = depth;
            node.order = order;
            return node;
        }

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        @Nullable
        @Override
        public Long getParentId() {
            return parentId;
        }

        @Override
        public void setParentId(@Nullable Long parentId) {
            this.parentId = parentId;
        }

        @Nullable
        @Override
        public TreeEntity getParent() {
            return null;
        }

        @Override
        public Short getDepth() {
            return depth;
        }

        @Override
        public void setDepth(Short depth) {
            this.depth = depth;
        }

        @Override
        public Integer getOrder() {
            return order;
        }

        @Override
        public void setOrder(Integer order) {
            this.order = order;
        }
    }

    private record Pair(long ancestor, long descendant) implements Comparable<Pair> {
        @Override
        public int compareTo(Pair other) {
            int result = Long.compare(ancestor, other.ancestor);
            return result != 0 ? result : Long.compare(descendant, other.descendant);
        }
    }

    private static final class Db {
        private final Map<Long, Node> nodes = new LinkedHashMap<>();
        private final Set<Pair> relations = new TreeSet<>();
        private final Entities entities = new Entities();
        private final Relations relationMapper = new Relations();
        private final TreeService<Node> service = new TreeService<>(entities, relationMapper);

        List<Long> idsByOrder(long siteId) {
            return nodes.values().stream().filter(node -> node.siteId == siteId)
                    .sorted(Comparator.comparing(Node::getOrder)).map(Node::getId).toList();
        }

        private Set<Long> descendants(Long ancestorId) {
            Set<Long> ids = new HashSet<>();
            for (Pair pair : relations) {
                if (pair.ancestor() == ancestorId) {
                    ids.add(pair.descendant());
                }
            }
            return ids;
        }

        private Set<Long> ancestors(Long descendantId) {
            Set<Long> ids = new HashSet<>();
            for (Pair pair : relations) {
                if (pair.descendant() == descendantId) {
                    ids.add(pair.ancestor());
                }
            }
            return ids;
        }

        /**
         * 与模型逐项比较，返回差异描述；另校验站点 2 未被触及
         */
        List<String> diff(Model model) {
            List<String> diff = new ArrayList<>();
            List<Long> expected = model.preOrder();
            Set<Pair> expectedRelations = new TreeSet<>();
            for (int i = 0; i < expected.size(); i++) {
                Long id = expected.get(i);
                Node node = nodes.get(id);
                if (node == null) {
                    diff.add(id + " 缺失");
                    continue;
                }
                if (node.order != i + 1) {
                    diff.add(id + ".order=" + node.order + "≠" + (i + 1));
                }
                if (node.depth != model.depthOf(id)) {
                    diff.add(id + ".depth=" + node.depth + "≠" + model.depthOf(id));
                }
                if (!Objects.equals(node.parentId, model.parentOf(id))) {
                    diff.add(id + ".parent=" + node.parentId + "≠" + model.parentOf(id));
                }
                for (Long descendant : model.subtree(id)) {
                    expectedRelations.add(new Pair(id, descendant));
                }
            }
            expectedRelations.add(new Pair(21L, 21L));
            expectedRelations.add(new Pair(21L, 22L));
            expectedRelations.add(new Pair(22L, 22L));
            if (!expectedRelations.equals(relations)) {
                Set<Pair> missing = new TreeSet<>(expectedRelations);
                missing.removeAll(relations);
                Set<Pair> extra = new TreeSet<>(relations);
                extra.removeAll(expectedRelations);
                diff.add("闭包表缺少 " + missing + " 多出 " + extra);
            }
            long siteNodes = nodes.values().stream().filter(node -> node.siteId == SITE).count();
            if (siteNodes != expected.size()) {
                diff.add("节点数=" + siteNodes + "≠" + expected.size());
            }
            if (!idsByOrder(OTHER_SITE).equals(List.of(21L, 22L))
                    || nodes.get(21L).order != 1 || nodes.get(22L).order != 2) {
                diff.add("站点 2 被改动：" + nodes.get(21L).order + "," + nodes.get(22L).order);
            }
            return diff;
        }

        /**
         * 语义同 ChannelMapper.xml：select 返回快照（调用方手里的对象不随后续更新变化）
         */
        private final class Entities implements TreeEntityMapper<Node> {
            @Override
            public int insert(Node bean) {
                nodes.put(bean.getId(), bean.copy());
                return 1;
            }

            @Override
            public int update(Node bean) {
                return 0;
            }

            @Override
            public int delete(Long id) {
                // fk_channel_parent ON DELETE RESTRICT
                if (nodes.values().stream().anyMatch(node -> id.equals(node.parentId))) {
                    throw new IllegalStateException("fk_parent: " + id + " 仍有子节点");
                }
                return nodes.remove(id) != null ? 1 : 0;
            }

            @Nullable
            @Override
            public Node select(Long id) {
                Node node = nodes.get(id);
                return node == null ? null : node.copy();
            }

            @Override
            public int updateDepth(Long id, short depth) {
                Set<Long> ids = descendants(id);
                ids.forEach(it -> nodes.get(it).depth = (short) (nodes.get(it).depth + depth));
                return ids.size();
            }

            @Override
            public int updateParentId(Long id, @Nullable Long parentId) {
                nodes.get(id).parentId = parentId;
                return 1;
            }

            @Nullable
            @Override
            public Integer maxOrder(@Nullable Long parentId, @Nullable Long siteId) {
                // 有上级时按闭包表取其子树（含自身）的最大值，不看站点；否则取站点内最大值
                Set<Long> scope = parentId != null ? descendants(parentId) : null;
                Integer max = null;
                for (Node node : nodes.values()) {
                    boolean inScope = scope != null ? scope.contains(node.id) : siteId == null || node.siteId == siteId;
                    if (inScope && (max == null || node.order > max)) {
                        max = node.order;
                    }
                }
                return max;
            }

            @Override
            public int updateOrder(int begin, int end, int num, @Nullable Long siteId) {
                int count = 0;
                for (Node node : nodes.values()) {
                    if (node.order >= begin && node.order <= end && (siteId == null || node.siteId == siteId)) {
                        node.order += num;
                        count++;
                    }
                }
                return count;
            }

            @Override
            public int batchUpdateOrderAndDepth(List<? extends TreeEntity> list) {
                for (TreeEntity item : list) {
                    Node node = nodes.get(item.getId());
                    node.order = item.getOrder();
                    node.depth = item.getDepth();
                }
                return list.size();
            }

            @Override
            public int updateOrderByParentId(Long parentId, int num) {
                Set<Long> ids = descendants(parentId);
                ids.forEach(it -> nodes.get(it).order += num);
                return ids.size();
            }
        }

        /**
         * 语义同 ChannelTreeMapper.xml
         */
        private final class Relations implements TreeRelationMapper {
            @Override
            public int save(Long ancestorId, Long descendantId) {
                return relations.add(new Pair(ancestorId, descendantId)) ? 1 : 0;
            }

            @Override
            public int saveBatch(List<? extends TreeRelation> list) {
                list.forEach(it -> relations.add(new Pair(it.getAncestorId(), it.getDescendantId())));
                return list.size();
            }

            @Override
            public int delete(Long ancestorId, Long descendantId) {
                return relations.remove(new Pair(ancestorId, descendantId)) ? 1 : 0;
            }

            @Override
            public int deleteByAncestorId(Long ancestorId) {
                Set<Long> subtree = descendants(ancestorId);
                int before = relations.size();
                relations.removeIf(pair -> subtree.contains(pair.descendant()));
                return before - relations.size();
            }

            @Override
            public int deleteAll() {
                int size = relations.size();
                relations.clear();
                return size;
            }

            @Override
            public int deleteBySiteId(Long siteId) {
                int before = relations.size();
                relations.removeIf(pair -> nodes.get(pair.ancestor()).siteId == siteId);
                return before - relations.size();
            }

            @Override
            public int move(Long id) {
                Set<Long> subtree = descendants(id);
                Set<Long> above = ancestors(id);
                above.remove(id);
                int before = relations.size();
                relations.removeIf(pair -> subtree.contains(pair.descendant()) && above.contains(pair.ancestor()));
                return before - relations.size();
            }

            @Override
            public int append(Long id, Long parentId) {
                Set<Long> above = ancestors(parentId);
                Set<Long> subtree = descendants(id);
                int count = 0;
                for (Long ancestor : above) {
                    for (Long descendant : subtree) {
                        count += relations.add(new Pair(ancestor, descendant)) ? 1 : 0;
                    }
                }
                return count;
            }

            @Override
            public int add(Long id, Long parentId) {
                int count = 0;
                for (Long ancestor : ancestors(parentId)) {
                    count += relations.add(new Pair(ancestor, id)) ? 1 : 0;
                }
                return count;
            }
        }
    }
}
