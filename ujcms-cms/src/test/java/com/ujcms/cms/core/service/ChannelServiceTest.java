package com.ujcms.cms.core.service;

import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.component.SlugGenerator;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticlePlacement;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.RoleArticle;
import com.ujcms.cms.core.domain.RoleChannel;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.mapper.ChannelExtMapper;
import com.ujcms.cms.core.mapper.ChannelMapper;
import com.ujcms.cms.core.mapper.ChannelTreeMapper;
import com.ujcms.cms.core.mapper.GroupAccessMapper;
import com.ujcms.cms.core.mapper.OrgArticleMapper;
import com.ujcms.cms.core.mapper.OrgChannelMapper;
import com.ujcms.cms.core.mapper.RoleArticleMapper;
import com.ujcms.cms.core.mapper.RoleChannelMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.service.ChannelService.BatchItem;
import com.ujcms.cms.core.service.ChannelService.BatchSettings;
import com.ujcms.cms.core.service.ChannelService.Grants;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.db.tree.TreeEntity;
import com.ujcms.common.db.tree.TreeMoveType;
import com.ujcms.common.db.tree.TreeRelation;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 栏目管理的服务层规则（docs/spec/channel-management/plan.md）：批量新增的校验与父子接线、默认授权、
 * 合并整棵子树、批量移动的顺序、整理树形结构、编辑时的修改时间与角色表。树形算法本身见 TreeServiceTest。
 */
class ChannelServiceTest {
    private static final long SITE = 1L;
    private static final long CHANNEL_MODEL = 100L;
    private static final long ARTICLE_MODEL = 200L;
    private static final long OTHER_ARTICLE_MODEL = 201L;

    private ChannelMapper mapper;
    private ChannelTreeMapper treeMapper;
    private ArticleMapper articleMapper;
    private GroupAccessMapper groupAccessMapper;
    private RoleArticleMapper roleArticleMapper;
    private RoleChannelMapper roleChannelMapper;
    private RoleService roleService;
    private GroupService groupService;
    private ModelService modelService;
    private ObjectProvider<ProcessDefinitionPort> processDefinitionPort;
    private ProcessDefinitionPort port;
    private SiteMapper siteMapper;
    private ChannelService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(ChannelMapper.class);
        // Mockito 对包装类型默认返回 0 而非 null：别名默认视为未被占用
        when(mapper.selectIdBySlug(anyLong(), any())).thenReturn(null);
        when(mapper.selectIdBySlugForUpdate(anyLong(), any())).thenReturn(null);
        treeMapper = mock(ChannelTreeMapper.class);
        articleMapper = mock(ArticleMapper.class);
        groupAccessMapper = mock(GroupAccessMapper.class);
        roleArticleMapper = mock(RoleArticleMapper.class);
        roleChannelMapper = mock(RoleChannelMapper.class);
        roleService = mock(RoleService.class);
        groupService = mock(GroupService.class);
        modelService = mock(ModelService.class);
        processDefinitionPort = mock(ObjectProvider.class);
        port = mock(ProcessDefinitionPort.class);
        siteMapper = mock(SiteMapper.class);
        when(processDefinitionPort.getIfAvailable()).thenReturn(port);
        // 本站部署了文章流程 flow
        when(port.isDeployed(SITE, Article.PROCESS_CATEGORY, "flow")).thenReturn(true);
        when(modelService.select(CHANNEL_MODEL)).thenReturn(model(CHANNEL_MODEL, Channel.MODEL_TYPE, SITE));
        when(modelService.select(ARTICLE_MODEL)).thenReturn(model(ARTICLE_MODEL, Article.MODEL_TYPE, SITE));
        SnowflakeSequence snowflakeSequence = mock(SnowflakeSequence.class);
        AtomicLong nextId = new AtomicLong(1000);
        when(snowflakeSequence.nextId()).thenAnswer(invocation -> nextId.incrementAndGet());
        service = new ChannelService(mock(HtmlService.class), mock(HtmlSanitizeService.class),
                mock(AttachmentService.class), modelService, mapper, mock(ChannelExtMapper.class), treeMapper,
                articleMapper, groupAccessMapper, roleArticleMapper, roleChannelMapper,
                mock(OrgArticleMapper.class), mock(OrgChannelMapper.class), snowflakeSequence,
                new SlugGenerator(mock(SiteMapper.class)), mock(ContentStatEvictor.class), roleService, groupService,
                processDefinitionPort, siteMapper);
    }

    private static Model model(long id, String type, Long siteId) {
        Model model = new Model();
        model.setId(id);
        model.setType(type);
        model.setSiteId(siteId);
        model.setScope(EntityConstants.SCOPE_PRIVATE);
        return model;
    }

    private static Channel channel(long id, Long parentId, String name) {
        Channel bean = new Channel();
        bean.setId(id);
        bean.setSiteId(SITE);
        bean.setParentId(parentId);
        bean.setName(name);
        bean.setSlug("c" + id);
        bean.setChannelModelId(CHANNEL_MODEL);
        bean.setArticleModelId(ARTICLE_MODEL);
        return bean;
    }

    private List<Channel> insertedChannels(int count) {
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(mapper, times(count)).insert(captor.capture());
        return captor.getAllValues();
    }

    private void assertBatchRejected(String code, BatchItem... items) {
        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.insertBatch(SITE, null, CHANNEL_MODEL, ARTICLE_MODEL, List.of(items)));
        assertEquals(code, e.getCode());
        verify(mapper, never()).insert(any());
    }

    // ---------------------------------------------------------------- 批量新增

    @Test
    void insertBatchWiresParentsByIndex() {
        List<Channel> created = service.insertBatch(SITE, null, CHANNEL_MODEL, ARTICLE_MODEL, List.of(
                new BatchItem(" 新闻 ", "news", -1),
                new BatchItem("国内", null, 0),
                new BatchItem("关于", "about", -1),
                new BatchItem("国际", "", 0),
                new BatchItem("亚洲", null, 3)));

        List<Channel> inserted = insertedChannels(5);
        assertEquals(created, inserted);
        Channel news = inserted.get(0);
        assertEquals("新闻", news.getName());
        assertEquals("news", news.getSlug());
        assertNull(news.getParentId());
        assertEquals(1, news.getDepth().intValue());
        assertEquals(news.getId(), inserted.get(1).getParentId());
        assertEquals(2, inserted.get(1).getDepth().intValue());
        assertNull(inserted.get(2).getParentId());
        assertEquals(news.getId(), inserted.get(3).getParentId());
        // 上级是同批新建的栏目：层级沿用其刚得到的 depth，无需回库读取
        assertEquals(inserted.get(3).getId(), inserted.get(4).getParentId());
        assertEquals(3, inserted.get(4).getDepth().intValue());
        // 纯中文名称无法 slugify，别名回退为 ID
        assertEquals(String.valueOf(inserted.get(1).getId()), inserted.get(1).getSlug());
        for (Channel bean : inserted) {
            assertEquals(SITE, bean.getSiteId());
            assertEquals(CHANNEL_MODEL, bean.getChannelModelId());
            assertEquals(ARTICLE_MODEL, bean.getArticleModelId());
            assertEquals(Channel.TYPE_NORMAL, bean.getType().shortValue());
        }
        // 上级已在手：不再逐个读取上级
        verify(mapper, never()).select(anyLong());
    }

    @Test
    void insertBatchGeneratedSlugAvoidsExplicitOnesInTheSameBatch() {
        service.insertBatch(SITE, null, CHANNEL_MODEL, ARTICLE_MODEL, List.of(
                new BatchItem("News", null, -1),
                new BatchItem("Latest", "news", -1)));
        List<Channel> inserted = insertedChannels(2);
        assertEquals("news-2", inserted.get(0).getSlug());
        assertEquals("news", inserted.get(1).getSlug());
    }

    @Test
    void insertBatchCopiesSettingsAndGrantsFromParent() {
        Channel parent = channel(50L, null, "上级");
        parent.setDepth((short) 2);
        parent.setNav(false);
        parent.setReal(false);
        parent.setAllowSearch(false);
        parent.setOrderDesc(false);
        parent.setPageSize((short) 15);
        parent.setChannelTemplate("channel_news");
        parent.setArticleTemplate("article_news");
        when(mapper.select(50L)).thenReturn(parent);
        when(groupAccessMapper.listGroupByChannelId(50L, null)).thenReturn(List.of(7L));
        when(roleArticleMapper.listRoleByChannelId(50L, null)).thenReturn(List.of(8L, 9L));
        when(roleChannelMapper.listRoleByChannelId(50L, null)).thenReturn(List.of(9L));

        service.insertBatch(SITE, 50L, CHANNEL_MODEL, ARTICLE_MODEL, List.of(
                new BatchItem("甲", null, -1), new BatchItem("乙", null, 0)));

        List<Channel> inserted = insertedChannels(2);
        assertEquals(Long.valueOf(50L), inserted.get(0).getParentId());
        assertEquals(3, inserted.get(0).getDepth().intValue());
        assertEquals(4, inserted.get(1).getDepth().intValue());
        for (Channel bean : inserted) {
            assertEquals(false, bean.getNav());
            assertEquals(false, bean.getReal());
            assertEquals(false, bean.getAllowSearch());
            assertEquals(false, bean.getOrderDesc());
            assertEquals(15, bean.getPageSize().intValue());
            assertEquals("channel_news", bean.getChannelTemplate());
            assertEquals("article_news", bean.getArticleTemplate());
        }
        // 两个新栏目都照搬所选上级的授权
        verify(groupAccessMapper, times(2)).insert(any());
        verify(roleArticleMapper, times(4)).insert(any());
        verify(roleChannelMapper, times(2)).insert(any());
        // 所选上级只读取一次
        verify(mapper, times(1)).select(50L);
    }

    @Test
    void insertBatchValidatesEverythingBeforeTheFirstInsert() {
        assertBatchRejected("error.channel.batch.parentInvalid",
                new BatchItem("甲", null, -1), new BatchItem("乙", null, 1));
        assertBatchRejected("error.channel.batch.parentInvalid", new BatchItem("甲", null, -2));
        assertBatchRejected("error.channel.batch.nameInvalid",
                new BatchItem("甲", null, -1), new BatchItem("  ", null, 0));
        assertBatchRejected("error.channel.batch.nameInvalid", new BatchItem("长".repeat(51), null, -1));
        assertBatchRejected("error.channel.slugInvalid", new BatchItem("甲", "a/b", -1));
        assertBatchRejected("error.channel.slugInvalid", new BatchItem("甲", "a".repeat(51), -1));
        // 同批重复不分大小写：MySQL 默认排序规则下唯一索引不分大小写
        assertBatchRejected("error.channel.batch.slugDuplicated",
                new BatchItem("甲", "News", -1), new BatchItem("乙", "news", -1));
        when(mapper.selectIdBySlug(SITE, "taken")).thenReturn(5L);
        assertBatchRejected("error.channel.slugExist",
                new BatchItem("甲", "fine", -1), new BatchItem("乙", "taken", -1));
    }

    @Test
    void insertBatchRejectsModelsOfAnotherSiteOrWrongType() {
        List<BatchItem> items = List.of(new BatchItem("甲", null, -1));
        // 文章模型当作栏目模型
        assertEquals("error.channel.modelInvalid", assertThrows(Http400Exception.class,
                () -> service.insertBatch(SITE, null, ARTICLE_MODEL, ARTICLE_MODEL, items)).getCode());
        // 其它站点的私有模型
        assertEquals("error.channel.modelInvalid", assertThrows(Http400Exception.class,
                () -> service.insertBatch(2L, null, CHANNEL_MODEL, ARTICLE_MODEL, items)).getCode());
        verify(mapper, never()).insert(any());
    }

    @Test
    void defaultGrantsAtRootAreGroupsAndRolesWithoutAllPermission() {
        Group group = new Group();
        group.setId(3L);
        Role role = new Role();
        role.setId(4L);
        when(groupService.listNotAllAccessPermission()).thenReturn(List.of(group));
        when(roleService.listNotAllArticlePermission(SITE)).thenReturn(List.of(role));
        when(roleService.listNotAllChannelPermission(SITE)).thenReturn(List.of());
        assertEquals(new Grants(List.of(3L), List.of(4L), List.of()), service.defaultGrants(SITE, null));
    }

    // ---------------------------------------------------------------- 编辑

    @Test
    void updateStampsModifiedAndWritesChannelRolesToRoleChannel() {
        Channel bean = channel(60L, null, "栏目");
        OffsetDateTime before = OffsetDateTime.now().minusDays(1);
        bean.setModified(before);

        service.update(bean, null, null, List.of(5L));

        assertTrue(bean.getModified().isAfter(before));
        verify(roleChannelMapper).deleteByChannelId(60L);
        verify(roleChannelMapper).insert(any(RoleChannel.class));
        verify(roleArticleMapper, never()).insert(any(RoleArticle.class));
    }

    // ---------------------------------------------------------------- 批量设置

    /**
     * 只给出 {@code fields} 与流程、分页两项取值的设置；其余取值为空
     */
    private static BatchSettings settings(List<String> fields, String processKey, Short pageSize) {
        return new BatchSettings(fields, null, null, null, processKey, null, null, null, pageSize);
    }

    private void assertBatchUpdateRejected(String code, BatchSettings settings) {
        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.updateBatch(SITE, List.of(10L, 11L), settings));
        assertEquals(code, e.getCode());
        verify(mapper, never()).updateSettings(anyLong(), anyCollection(), anyCollection(), any(), any());
    }

    @Test
    void listBatchTargetIdsAddsEachSubtreeOnce() {
        when(mapper.listIdsInSite(eq(SITE), anyCollection())).thenReturn(List.of(10L, 11L, 20L));
        when(mapper.listIdsByAncestorId(10L)).thenReturn(List.of(10L, 11L, 12L));
        when(mapper.listIdsByAncestorId(20L)).thenReturn(List.of(20L));

        // 不含下级：就是所选的栏目
        assertEquals(List.of(10L, 11L, 20L),
                new ArrayList<>(service.listBatchTargetIds(SITE, List.of(10L, 11L, 20L), false)));
        // 含下级：11 已随 10 计入，不再单独展开
        assertEquals(List.of(10L, 11L, 12L, 20L),
                new ArrayList<>(service.listBatchTargetIds(SITE, List.of(10L, 11L, 20L), true)));
        verify(mapper, never()).listIdsByAncestorId(11L);
    }

    @Test
    void listBatchTargetIdsRejectsChannelsOutsideTheSite() {
        when(mapper.listIdsInSite(eq(SITE), anyCollection())).thenReturn(List.of(10L));
        assertThrows(Http404Exception.class, () -> service.listBatchTargetIds(SITE, List.of(10L, 99L), true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateBatchWritesOnlyTheListedFieldsAndStampsModified() {
        when(mapper.updateSettings(anyLong(), anyCollection(), anyCollection(), any(), any())).thenReturn(2);

        int updated = service.updateBatch(SITE, List.of(10L, 11L),
                settings(List.of("processKey", "pageSize"), "flow", (short) 30));

        assertEquals(2, updated);
        ArgumentCaptor<Collection<String>> fields = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Channel> values = ArgumentCaptor.forClass(Channel.class);
        ArgumentCaptor<OffsetDateTime> modified = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(mapper).updateSettings(eq(SITE), eq(List.of(10L, 11L)), fields.capture(), values.capture(),
                modified.capture());
        assertEquals(Set.of("processKey", "pageSize"), Set.copyOf(fields.getValue()));
        assertEquals("flow", values.getValue().getProcessKey());
        assertEquals((short) 30, values.getValue().getPageSize());
        assertTrue(modified.getValue().isAfter(OffsetDateTime.now().minusMinutes(1)));
        // 没有改文章模型：不查文章
        verify(mapper, never()).listIdsHoldingArticlesUnderOtherModel(anyCollection(), anyLong());
    }

    @Test
    void updateBatchClearsProcessKeyAndTemplatesListedWithoutValue() {
        BatchSettings settings = new BatchSettings(List.of("processKey", "channelTemplate", "articleTemplate"),
                null, " ", null, "", null, null, null, null);

        service.updateBatch(SITE, List.of(10L), settings);

        ArgumentCaptor<Channel> values = ArgumentCaptor.forClass(Channel.class);
        verify(mapper).updateSettings(eq(SITE), eq(List.of(10L)), anyCollection(), values.capture(), any());
        assertNull(values.getValue().getProcessKey());
        assertNull(values.getValue().getChannelTemplate());
        assertNull(values.getValue().getArticleTemplate());
    }

    @Test
    void updateBatchValidatesEverythingBeforeTheFirstWrite() {
        // 没有勾选任何属性、未知的属性
        assertBatchUpdateRejected("error.channel.batchUpdate.fieldInvalid", settings(List.of(), null, null));
        assertBatchUpdateRejected("error.channel.batchUpdate.fieldInvalid", settings(List.of("slug"), null, null));
        // 不可为空的属性勾选了却没有取值
        assertBatchUpdateRejected("error.channel.batchUpdate.fieldInvalid", settings(List.of("nav"), null, null));
        assertBatchUpdateRejected("error.channel.batchUpdate.fieldInvalid",
                settings(List.of("articleModelId"), null, null));
        // 分页条数越界
        assertBatchUpdateRejected("error.channel.pageSizeInvalid", settings(List.of("pageSize"), null, (short) 0));
        assertBatchUpdateRejected("error.channel.pageSizeInvalid", settings(List.of("pageSize"), null, (short) 201));
        // 模板名不得含上级目录
        assertBatchUpdateRejected("error.channel.batchUpdate.fieldInvalid", new BatchSettings(
                List.of("channelTemplate"), null, "../channel", null, null, null, null, null, null));
        // 栏目模型当作文章模型
        assertBatchUpdateRejected("error.channel.modelInvalid", new BatchSettings(
                List.of("articleModelId"), CHANNEL_MODEL, null, null, null, null, null, null, null));
        // 有一项不合格，其余合格的也不写入
        assertBatchUpdateRejected("error.channel.pageSizeInvalid",
                settings(List.of("processKey", "pageSize"), "flow", (short) 0));
    }

    @Test
    void updateBatchRefusesArticleModelChangeWhereArticlesExist() {
        when(modelService.select(OTHER_ARTICLE_MODEL))
                .thenReturn(model(OTHER_ARTICLE_MODEL, Article.MODEL_TYPE, SITE));
        when(mapper.select(11L)).thenReturn(channel(11L, 10L, "有文章"));
        BatchSettings settings = new BatchSettings(List.of("articleModelId"), OTHER_ARTICLE_MODEL,
                null, null, null, null, null, null, null);

        // 目标栏目里没有“已有文章且模型不同”的：照常写入
        when(mapper.listIdsHoldingArticlesUnderOtherModel(anyCollection(), eq(OTHER_ARTICLE_MODEL)))
                .thenReturn(List.of());
        service.updateBatch(SITE, List.of(10L, 11L), settings);
        ArgumentCaptor<Channel> values = ArgumentCaptor.forClass(Channel.class);
        verify(mapper).updateSettings(eq(SITE), eq(List.of(10L, 11L)), anyCollection(), values.capture(), any());
        assertEquals(OTHER_ARTICLE_MODEL, values.getValue().getArticleModelId());

        // 有这样的栏目：整批拒绝并指明栏目
        when(mapper.listIdsHoldingArticlesUnderOtherModel(anyCollection(), eq(OTHER_ARTICLE_MODEL)))
                .thenReturn(List.of(11L));
        Http400Exception e = assertThrows(Http400Exception.class,
                () -> service.updateBatch(SITE, List.of(10L, 11L), settings));
        assertEquals("error.channel.batchUpdate.modelHasArticles", e.getCode());
        verify(mapper, times(1)).updateSettings(anyLong(), anyCollection(), anyCollection(), any(), any());
    }

    @Test
    void updateBatchRefusesAProcessNotDeployedForTheSite() {
        // 没部署的流程、别的类别的流程（端口按类别查）：整批拒绝
        assertBatchUpdateRejected("error.channel.processInvalid", settings(List.of("processKey"), "nowhere", null));
        // 留空即无流程，不问端口
        service.updateBatch(SITE, List.of(10L), settings(List.of("processKey"), "", null));
        verify(port, never()).isDeployed(anyLong(), any(), eq(""));
    }

    @Test
    void updateBatchSplitsLongIdListsIntoChunks() {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= ChannelService.BATCH_MAX_SIZE + 1; id++) {
            ids.add(id);
        }
        when(mapper.updateSettings(anyLong(), anyCollection(), anyCollection(), any(), any()))
                .thenAnswer(invocation -> ((Collection<?>) invocation.getArgument(1)).size());

        assertEquals(ids.size(), service.updateBatch(SITE, ids, settings(List.of("processKey"), null, null)));

        verify(mapper, times(2)).updateSettings(anyLong(), anyCollection(), anyCollection(), any(), any());
    }

    // ---------------------------------------------------------------- 合并

    /**
     * 源栏目 10 之下有 11、12；目标栏目 20
     */
    private void mergeFixture() {
        Channel c10 = channel(10L, null, "源");
        Channel c11 = channel(11L, 10L, "子一");
        Channel c12 = channel(12L, 10L, "子二");
        Channel c20 = channel(20L, null, "目标");
        when(mapper.select(10L)).thenReturn(c10);
        when(mapper.select(11L)).thenReturn(c11);
        when(mapper.select(12L)).thenReturn(c12);
        when(mapper.select(20L)).thenReturn(c20);
        when(mapper.listIdsByAncestorId(10L)).thenReturn(List.of(10L, 11L, 12L));
        when(mapper.listIdsByAncestorId(11L)).thenReturn(List.of(11L));
        when(mapper.selectAll(any(), anyBoolean(), isNull(), isNull())).thenReturn(List.of(c10, c11, c12));
        when(mapper.listChildren(10L)).thenReturn(List.of(c11, c12));
        when(articleMapper.updateChannelId(10L, 20L)).thenReturn(7);
    }

    @Test
    void mergeBatchMergesTheBranchOnceAndDeletesBottomUp() {
        mergeFixture();

        // 选中上级时控件会连同子栏目一起勾选：子栏目随上级合并，不再单独处理
        int moved = service.mergeBatch(List.of(10L, 11L), 20L, SITE);

        assertEquals(7, moved);
        verify(articleMapper, times(1)).updateChannelId(anyLong(), anyLong());
        verify(articleMapper).updateChannelId(10L, 20L);
        InOrder order = inOrder(articleMapper, mapper);
        order.verify(articleMapper).updateChannelId(10L, 20L);
        order.verify(mapper).delete(11L);
        order.verify(mapper).delete(12L);
        order.verify(mapper).delete(10L);
        verify(mapper, never()).delete(20L);
    }

    @Test
    void mergeRejectsDifferentArticleModelOnlyWhereArticlesExist() {
        mergeFixture();
        Channel c12 = mapper.select(12L);
        c12.setArticleModelId(OTHER_ARTICLE_MODEL);

        // 文章模型不同但没有文章：不妨碍合并
        assertEquals(7, service.merge(10L, 20L, SITE));

        when(articleMapper.existsByChannelId(12L)).thenReturn(1);
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.merge(10L, 20L, SITE));
        assertEquals("error.channel.merge.modelMismatch", e.getCode());
        // 第二次合并在写入之前即被拒绝
        verify(articleMapper, times(1)).updateChannelId(anyLong(), anyLong());
    }

    @Test
    void mergeRejectsTargetInsideTheSourceBranch() {
        mergeFixture();
        Channel c11 = mapper.select(11L);
        c11.setParent(mapper.select(10L));

        Http400Exception e = assertThrows(Http400Exception.class, () -> service.merge(10L, 11L, SITE));
        assertEquals("error.channel.moveToDescendant", e.getCode());
        assertEquals("error.channel.moveToDescendant",
                assertThrows(Http400Exception.class, () -> service.merge(10L, 10L, SITE)).getCode());
        verify(articleMapper, never()).updateChannelId(anyLong(), anyLong());
        verify(mapper, never()).delete(anyLong());
    }

    // ---- 合并后的落位（D21）：源栏目子树与目标栏目里，同一落位家族不得有两处

    private static ArticlePlacement placement(long id, Long srcId, long channelId) {
        return new ArticlePlacement(id, srcId, channelId);
    }

    @Test
    void mergeLocksThePlacementsAndTheSourcesElsewhereBeforeMoving() {
        mergeFixture();
        // 11 里有源文章 1 的引用 101，源文章 1 在别处；20 里有源文章 2 自身；12 里有 2 的复制 201 —— 2 会在目标里出现两次
        when(articleMapper.lockPlacementsInChannels(List.of(10L, 11L, 12L, 20L))).thenReturn(List.of(
                placement(2L, null, 20L), placement(101L, 1L, 11L), placement(201L, 2L, 12L)));
        Article two = new Article();
        two.setId(2L);
        two.getExt().setTitle("两处");
        when(articleMapper.select(2L)).thenReturn(two);

        Http400Exception e = assertThrows(Http400Exception.class, () -> service.merge(10L, 20L, SITE));

        assertEquals("error.channel.merge.placementCollision", e.getCode());
        assertArrayEquals(new String[]{"两处", "1"}, e.getArgs());
        // 站点行锁在先（D22 的锁序），再锁定读，再锁住在别处的源文章 1（2 已在锁定读之内），然后才判定；没有写入
        InOrder order = inOrder(siteMapper, articleMapper);
        order.verify(siteMapper).lockById(SITE);
        order.verify(articleMapper).lockPlacementsInChannels(List.of(10L, 11L, 12L, 20L));
        order.verify(articleMapper).lockByIds(List.of(1L));
        verify(articleMapper, never()).updateChannelId(anyLong(), anyLong());
        verify(mapper, never()).delete(anyLong());
    }

    @Test
    void mergeAcceptsFamiliesWithOnePlacementInTheMergedChannels() {
        mergeFixture();
        // 1 的引用在 11，1 自身在别处（不在合并范围内）；3 自身在 20，它的推送子行在站外栏目 99（不在范围内）
        when(articleMapper.lockPlacementsInChannels(List.of(10L, 11L, 12L, 20L))).thenReturn(List.of(
                placement(101L, 1L, 11L), placement(3L, null, 20L)));

        assertEquals(7, service.merge(10L, 20L, SITE));

        verify(articleMapper).lockByIds(List.of(1L));
        verify(articleMapper).updateChannelId(10L, 20L);
    }

    @Test
    void mergeTakesNoExtraLockWhenEverySourceIsInside() {
        mergeFixture();
        when(articleMapper.lockPlacementsInChannels(anyCollection())).thenReturn(List.of(
                placement(1L, null, 11L), placement(4L, null, 20L)));

        service.merge(10L, 20L, SITE);

        verify(articleMapper, never()).lockByIds(anyCollection());
    }

    // ---------------------------------------------------------------- 审核流程（D23）

    @Test
    void validateProcessKeyAcceptsBlankAndADeployedArticleProcess() {
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, null));
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, ""));
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, "flow"));
    }

    @Test
    void validateProcessKeyRejectsAProcessNotDeployedForTheSiteOrOfAnotherCategory() {
        Http400Exception e = assertThrows(Http400Exception.class, () -> service.validateProcessKey(SITE, "nowhere"));
        assertEquals("error.channel.processInvalid", e.getCode());
        assertArrayEquals(new String[]{"nowhere"}, e.getArgs());
        // 查的是文章类别：表单流程不算
        verify(port).isDeployed(SITE, Article.PROCESS_CATEGORY, "nowhere");
    }

    @Test
    void validateProcessKeyRejectsAnyKeyWithoutThePort() {
        when(processDefinitionPort.getIfAvailable()).thenReturn(null);
        assertThrows(Http400Exception.class, () -> service.validateProcessKey(SITE, "flow"));
        assertDoesNotThrow(() -> service.validateProcessKey(SITE, null));
    }

    // ---------------------------------------------------------------- 批量移动、整理

    @Test
    void moveBatchRereadsEachTimeAndReversesForAfter() {
        for (long id = 1; id <= 4; id++) {
            when(mapper.select(id)).thenReturn(channel(id, null, "栏目" + id));
        }

        service.moveBatch(List.of(1L, 2L, 3L), 4L, TreeMoveType.AFTER, SITE);

        // “之后”逆序执行：依次把 3、2、1 放到 4 之后，结果才是 4、1、2、3
        InOrder order = inOrder(mapper);
        order.verify(mapper).select(3L);
        order.verify(mapper).select(2L);
        order.verify(mapper).select(1L);
        // 目标栏目每次重新读取：上一次移动已改变它的 order
        verify(mapper, times(3)).select(4L);
    }

    @Test
    void moveBatchRejectsOtherSitesAndOwnDescendants() {
        Channel foreign = channel(1L, null, "外站");
        foreign.setSiteId(2L);
        Channel parent = channel(2L, null, "上级");
        Channel child = channel(3L, 2L, "下级");
        child.setParent(parent);
        when(mapper.select(1L)).thenReturn(foreign);
        when(mapper.select(2L)).thenReturn(parent);
        when(mapper.select(3L)).thenReturn(child);

        assertThrows(Http400Exception.class, () -> service.moveBatch(List.of(1L), 2L, TreeMoveType.INNER, SITE));
        assertEquals("error.channel.moveToDescendant", assertThrows(Http400Exception.class,
                () -> service.moveBatch(List.of(2L), 3L, TreeMoveType.INNER, SITE)).getCode());
        verify(mapper, never()).updateParentId(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tidyTreeKeepsSiblingOrderAndRebuildsRelations() {
        // Mapper 按 order_ 返回：3 排在 1 之前，2 是 1 的下级
        when(mapper.listBySiteIdForTidy(SITE)).thenReturn(new ArrayList<>(List.of(
                channel(3L, null, "丙"), channel(1L, null, "甲"), channel(2L, 1L, "乙"))));

        service.tidyTree(SITE);

        ArgumentCaptor<List<TreeEntity>> entities = ArgumentCaptor.forClass(List.class);
        verify(mapper).batchUpdateOrderAndDepth(entities.capture());
        List<String> rows = entities.getValue().stream()
                .map(it -> it.getId() + ":" + it.getOrder() + ":" + it.getDepth()).toList();
        assertEquals(List.of("3:1:1", "1:2:1", "2:3:2"), rows);
        ArgumentCaptor<List<TreeRelation>> relations = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(mapper, treeMapper);
        order.verify(mapper).batchUpdateOrderAndDepth(anyList());
        order.verify(treeMapper).deleteBySiteId(eq(SITE));
        order.verify(treeMapper).saveBatch(relations.capture());
        List<String> pairs = relations.getValue().stream()
                .map(it -> it.getAncestorId() + ">" + it.getDescendantId()).sorted().toList();
        assertEquals(List.of("1>1", "1>2", "2>2", "3>3"), pairs);
    }
}
