package com.ujcms.cms.core.support;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Dict;
import com.ujcms.cms.core.domain.DictType;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.PerformanceType;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.UserExt;
import com.ujcms.cms.core.domain.UserRole;
import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.cms.core.generator.LuceneGenerator;
import com.ujcms.cms.core.mapper.ConfigMapper;
import com.ujcms.cms.core.mapper.DictMapper;
import com.ujcms.cms.core.mapper.DictTypeMapper;
import com.ujcms.cms.core.mapper.GroupMapper;
import com.ujcms.cms.core.mapper.ModelMapper;
import com.ujcms.cms.core.mapper.OrgMapper;
import com.ujcms.cms.core.mapper.OrgTreeMapper;
import com.ujcms.cms.core.mapper.PerformanceTypeMapper;
import com.ujcms.cms.core.mapper.RoleMapper;
import com.ujcms.cms.core.mapper.SiteMapper;
import com.ujcms.cms.core.mapper.SiteTreeMapper;
import com.ujcms.cms.core.mapper.UserExtMapper;
import com.ujcms.cms.core.mapper.UserMapper;
import com.ujcms.cms.core.mapper.UserRoleMapper;
import com.ujcms.cms.core.service.InstallService;
import com.ujcms.cms.core.service.ThemePackageService;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.mapper.MessageBoardTypeMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 系统核心引导（install-flow §4 P2 / D8）：空库首次启动时，经 Mapper 以固定 ID 写入
 * 系统骨架行，随后写入持久化安装状态 {@code coreReady} 并生成一次性安装令牌（D12）。
 * 取代旧四方言 {@code db/data-*.sql} 引导脚本——纯 Java、无方言、单事务，杜绝方言副本漂移。
 *
 * <p>只写「系统行」：config、组织 0/1、用户组 1/8/10、用户 0/1（含 ext/角色/关联）、
 * 站点 1 骨架与树、系统模型（1 全局 / 2 用户 / 13 站点）、系统字典类型与系统字典、
 * 留言/绩效类型。演示口味的行（内容模型、演示字典、表单类型、全局焦点区块）已迁入
 * 默认主题种子，由种子导入以来源三元组创建（C1/C2/D14）；{@code ujcms_seq} 化石行
 * 不再写入——{@code SeqService} 按需自建，且现存消费方（访问日志）并无固定 ID 行可撞。
 *
 * <p>引导结束于 SQL 阶段原本结束之处（P1 接缝不变）：{@code coreReady} 状态 + 安装令牌。
 * 演示种子的导入属安装引擎的主题步骤（{@link InstallService#installTheme}），由控制台
 * 向导或 {@code ujcms.install.auto} 清单驱动。既有库（检查表非空）上绝不重跑；状态非
 * INSTALLED 时每次启动重新生成并打印安装令牌（以最后一次打印为准）。
 *
 * @author PONY
 */
@Component
// Spring 桥接的 MyBatis 二级缓存（Global/Site/GroupSpringCache 静态上下文持有者）须先于
// 本引导实例化：Mapper 写入提交时会触发对应缓存命名空间的 clear()
@DependsOn({"liquibase", "globalSpringCache", "siteSpringCache", "groupSpringCache"})
@ConditionalOnProperty(prefix = "ujcms", name = "bootstrap-enabled", matchIfMissing = true)
public class SystemCoreBootstrap implements InitializingBean {
    protected final Logger logger = LoggerFactory.getLogger(SystemCoreBootstrap.class);

    /**
     * 首装引导固定 ID（D4）：站点 1 / 管理员 1
     */
    private static final long INIT_SITE_ID = InstallService.INIT_SITE_ID;
    private static final long INIT_USER_ID = InstallService.INIT_USER_ID;

    /**
     * 空库判定检查表（与安装状态机的 NOT_INSTALLED 判定同源）
     */
    private static final String CHECK_TABLE = "ujcms_config";

    private final DataSource dataSource;
    private final TransactionTemplate transactionTemplate;
    private final ConfigMapper configMapper;
    private final OrgMapper orgMapper;
    private final OrgTreeMapper orgTreeMapper;
    private final ModelMapper modelMapper;
    private final GroupMapper groupMapper;
    private final UserMapper userMapper;
    private final UserExtMapper userExtMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final SiteMapper siteMapper;
    private final SiteTreeMapper siteTreeMapper;
    private final DictTypeMapper dictTypeMapper;
    private final DictMapper dictMapper;
    private final MessageBoardTypeMapper messageBoardTypeMapper;
    private final PerformanceTypeMapper performanceTypeMapper;
    private final LuceneGenerator luceneGenerator;
    private final InstallService installService;
    private final ThemePackageService themePackageService;
    private final Props props;

    public SystemCoreBootstrap(DataSource dataSource, PlatformTransactionManager transactionManager,
                               ConfigMapper configMapper, OrgMapper orgMapper, OrgTreeMapper orgTreeMapper,
                               ModelMapper modelMapper, GroupMapper groupMapper, UserMapper userMapper,
                               UserExtMapper userExtMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                               SiteMapper siteMapper, SiteTreeMapper siteTreeMapper, DictTypeMapper dictTypeMapper,
                               DictMapper dictMapper, MessageBoardTypeMapper messageBoardTypeMapper,
                               PerformanceTypeMapper performanceTypeMapper, LuceneGenerator luceneGenerator,
                               InstallService installService, ThemePackageService themePackageService, Props props) {
        this.dataSource = dataSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.configMapper = configMapper;
        this.orgMapper = orgMapper;
        this.orgTreeMapper = orgTreeMapper;
        this.modelMapper = modelMapper;
        this.groupMapper = groupMapper;
        this.userMapper = userMapper;
        this.userExtMapper = userExtMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.siteMapper = siteMapper;
        this.siteTreeMapper = siteTreeMapper;
        this.dictTypeMapper = dictTypeMapper;
        this.dictMapper = dictMapper;
        this.messageBoardTypeMapper = messageBoardTypeMapper;
        this.performanceTypeMapper = performanceTypeMapper;
        this.luceneGenerator = luceneGenerator;
        this.installService = installService;
        this.themePackageService = themePackageService;
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    public void initialize() {
        Integer hasData = new JdbcTemplate(dataSource)
                .queryForObject("select count(*) from " + CHECK_TABLE, Integer.TYPE);
        if (hasData != null && hasData > 0) {
            // 既有库绝不重跑引导；未完成安装（含状态列为 NULL 的崩溃续装窗口行）
            // 则重新生成并打印安装令牌，供向导续装；并确保内置包已注册（P3 D6——
            // 骨架写入与注册之间崩溃的续装路径同样要有包可装）
            if (!installService.isInstalled()) {
                installService.refreshInstallToken(props.getInstall().getToken());
                themePackageService.ensureBundledRegistered();
            } else {
                // 已安装：清理残留令牌文件（D49——另一节点完成安装、完成翻转后崩溃）
                installService.discardTokenFile();
            }
            return;
        }
        // 单事务写入系统骨架：崩溃即整体回滚，下次启动重新引导，绝无半成品骨架
        transactionTemplate.executeWithoutResult(status -> writeSystemRows());
        logger.info("System core bootstrap completed (fixed-id skeleton rows written)");
        // 初始化 Lucene（站点名/域名/管理员凭据归安装引擎——ujcms.install.* 清单或控制台向导）
        initLucene();
        // 写入安装状态 coreReady + 一次性安装令牌（预设令牌为空时随机生成并打印日志）
        installService.refreshInstallToken(props.getInstall().getToken());
        // 内置默认主题包注册入库（install-flow §6 P3 / D6：裸 JAR 首装由安装引擎从库回退安装；
        // 按 sha256 幂等，失败不阻断启动）
        themePackageService.ensureBundledRegistered();
    }

    private void writeSystemRows() {
        // 外键序：站点依赖组织/模型，角色依赖站点，用户角色关联依赖用户与角色
        insertConfig();
        insertOrgs();
        insertSystemModels();
        insertGroups();
        insertUsers();
        insertSite();
        insertRole();
        insertUserRole();
        insertSysDicts();
        insertMessageBoardTypes();
        insertPerformanceTypes();
    }

    private void insertConfig() {
        Config config = new Config();
        config.setId(1L);
        config.setPort(8080);
        config.setMultiDomain(false);
        config.setDefaultSiteId(INIT_SITE_ID);
        config.setUploadSettings(CONFIG_UPLOAD_SETTINGS);
        config.setGreySettings("{\"enabled\":false,\"greyDates\":\"09-08,12-13\"}");
        config.setRegisterSettings(CONFIG_REGISTER_SETTINGS);
        config.setCustomsSettings("{}");
        configMapper.insert(config);
    }

    private void insertOrgs() {
        orgMapper.insert(org(0L, "前台组织", 1));
        orgMapper.insert(org(1L, "总部", 2));
        orgTreeMapper.save(0L, 0L);
        orgTreeMapper.save(1L, 1L);
    }

    private Org org(Long id, String name, int order) {
        Org org = new Org();
        org.setId(id);
        org.setName(name);
        org.setDepth((short) 1);
        org.setOrder(order);
        return org;
    }

    private void insertSystemModels() {
        // 全局自定义字段不预置。13.1 之前预置一个“第三方登录”开关（customs.oauth），其职责已由“全局设置 › 第三方登录”
        // （Config.Oauth，各服务商自带启用开关）取代；既有库里的该字段不再被默认主题读取，可在模型管理中删除
        modelMapper.insert(model(1L, "全局设置模型", "global", null));
        modelMapper.insert(model(2L, "用户模型", User.MODEL_TYPE, null));
        modelMapper.insert(model(13L, "站点设置模型", "site", null));
    }

    private Model model(Long id, String name, String type, @Nullable String customs) {
        Model model = new Model();
        model.setId(id);
        model.setName(name);
        model.setType(type);
        model.setScope(EntityConstants.SCOPE_GLOBAL);
        model.setOrder(32767);
        model.setCustoms(customs);
        return model;
    }

    private void insertGroups() {
        groupMapper.insert(group(1L, "游客组", "未登录的用户属于游客组", (short) 1, 1));
        groupMapper.insert(group(8L, "管理组", "后台管理员默认属于管理组", (short) 2, 2));
        groupMapper.insert(group(10L, "普通组", "前台注册用户默认属于普通组", (short) 2, 3));
    }

    private Group group(Long id, String name, String description, Short type, int order) {
        Group group = new Group();
        group.setId(id);
        group.setName(name);
        group.setDescription(description);
        group.setAllAccessPermission(true);
        group.setType(type);
        group.setOrder(order);
        return group;
    }

    private void insertUsers() {
        User anonymous = new User();
        anonymous.setId(0L);
        anonymous.setGroupId(1L);
        anonymous.setOrgId(0L);
        anonymous.setUsername("anonymous");
        anonymous.setRank((short) 0);
        anonymous.setType(User.TYPE_MEMBER);
        anonymous.setStatus(User.STATUS_LOCKED);
        userMapper.insert(anonymous);
        userExtMapper.insert(userExt(0L));

        User admin = new User();
        admin.setId(INIT_USER_ID);
        admin.setGroupId(8L);
        admin.setOrgId(1L);
        admin.setUsername("admin");
        admin.setPassword(ADMIN_PASSWORD_HASH);
        admin.setRank((short) 0);
        admin.setType(User.TYPE_NORMAL);
        admin.setStatus(User.STATUS_ENABLED);
        // 引导口令众所周知：安装步骤设置凭据前必须改密（configureAdmin 默认清除，C6）
        admin.setPwdMustChange(true);
        userMapper.insert(admin);
        userExtMapper.insert(userExt(INIT_USER_ID));
    }

    private void insertUserRole() {
        UserRole userRole = new UserRole(INIT_USER_ID, 1L);
        userRole.setOrder(9999);
        userRoleMapper.insert(userRole);
    }

    private UserExt userExt(Long id) {
        UserExt ext = new UserExt();
        ext.setId(id);
        return ext;
    }

    private void insertSite() {
        Site site = new Site();
        site.setId(INIT_SITE_ID);
        site.setOrgId(1L);
        site.setModelId(13L);
        site.setName("UJCMS演示站");
        site.setProtocol("http");
        site.setDomain("localhost");
        site.setTheme("default");
        site.setMobileTheme("default");
        site.setPageSize((short) 10);
        site.setStaticFile("/index.html");
        site.setEditorSettings(SITE_EDITOR_SETTINGS);
        site.setWatermarkSettings(SITE_WATERMARK_SETTINGS);
        site.setHtmlSettings(SITE_HTML_SETTINGS);
        site.setMessageBoardSettings("{\"enabled\":true,\"loginRequired\":true}");
        site.setStatus(Site.STATUS_NORMAL);
        site.setDepth((short) 1);
        site.setOrder(1);
        siteMapper.insert(site);
        siteTreeMapper.save(INIT_SITE_ID, INIT_SITE_ID);
    }

    private void insertRole() {
        Role role = new Role();
        role.setId(1L);
        role.setSiteId(INIT_SITE_ID);
        role.setName("管理角色");
        role.setAllPermission(true);
        role.setAllGrantPermission(true);
        role.setGlobalPermission(true);
        role.setAllArticlePermission(true);
        role.setAllChannelPermission(true);
        role.setDataScope((short) 1);
        role.setAllStatusPermission(false);
        role.setRank((short) 1);
        role.setType((short) 4);
        role.setScope((short) 0);
        role.setOrder(1);
        roleMapper.insert(role);
    }

    private void insertSysDicts() {
        dictTypeMapper.insert(sysDictType(1L, "文章来源", "sys_article_source"));
        dictTypeMapper.insert(sysDictType(2L, "流程类型", "sys_process_category"));
        // 站点语言（docs/spec/dictionary P4）：站点表单/站点设置的语言下拉取自此字典（仍可直填标签）
        dictTypeMapper.insert(sysDictType(3L, "站点语言", "sys_site_lang"));
        dictMapper.insert(sysDict(1L, 2L, "文章审核", "sys_article", 32767));
        dictMapper.insert(sysDict(2L, 2L, "表单审核", "sys_form", 999999));
        // 文章来源示例条目（中性占位；非 sys，可由操作员删改）——
        // 系统字典类型在主题种子里为 linked 行，种子不会为其补条目，故留在骨架里
        dictMapper.insert(dict(3L, 1L, "本站原创", "1", 1));
        dictMapper.insert(dict(4L, 1L, "读者投稿", "2", 2));
        // 站点语言出厂条目（非 sys，操作员可增删改）。既有库由 db.changelog-13.0-dict.yaml 的
        // 13.0-dict-sys-site-lang 变更集补齐同一批固定 ID 行——两处清单须保持一致
        long id = 5L;
        int order = 1;
        for (String[] lang : SITE_LANGS) {
            dictMapper.insert(dict(id++, 3L, lang[1], lang[0], order++));
        }
    }

    /**
     * 站点语言出厂条目 {@code {value(BCP-47), name}}，固定 ID 5..16——语种范围对齐政府门户通行做法
     * （如上海市政府官网的 12 种语言），名称用各语言自称；操作员可在“配置 - 字典管理 - 站点语言”增删改
     */
    private static final String[][] SITE_LANGS = {
            {"zh-CN", "简体中文"}, {"zh-TW", "繁體中文"}, {"en", "English"},
            {"ja", "日本語"}, {"ko", "한국어"}, {"de", "Deutsch"},
            {"fr", "Français"}, {"es", "Español"}, {"pt", "Português"},
            {"ru", "Русский"}, {"ar", "العربية"}, {"it", "Italiano"},
    };

    private DictType sysDictType(Long id, String name, String alias) {
        DictType type = new DictType();
        type.setId(id);
        type.setName(name);
        type.setAlias(alias);
        type.setScope(EntityConstants.SCOPE_GLOBAL);
        type.setDataType(DictType.DATA_TYPE_STRING);
        type.setOrder(32767);
        type.setSys(true);
        return type;
    }

    private Dict sysDict(Long id, Long typeId, String name, String value, int order) {
        Dict dict = dict(id, typeId, name, value, order);
        dict.setSys(true);
        return dict;
    }

    private Dict dict(Long id, Long typeId, String name, String value, int order) {
        Dict dict = new Dict();
        dict.setId(id);
        dict.setTypeId(typeId);
        dict.setName(name);
        dict.setValue(value);
        dict.setOrder(order);
        dict.setSys(false);
        dict.setEnabled(true);
        return dict;
    }

    private void insertMessageBoardTypes() {
        messageBoardTypeMapper.insert(messageBoardType(62271616407096322L, "咨询"));
        messageBoardTypeMapper.insert(messageBoardType(62271625263893505L, "建议"));
    }

    private MessageBoardType messageBoardType(Long id, String name) {
        MessageBoardType type = new MessageBoardType();
        type.setId(id);
        type.setSiteId(INIT_SITE_ID);
        type.setName(name);
        type.setOrder(id);
        return type;
    }

    private void insertPerformanceTypes() {
        performanceTypeMapper.insert(performanceType(62401880487402498L, "资讯类", 2));
        performanceTypeMapper.insert(performanceType(62401886289697793L, "分析类", 5));
    }

    private PerformanceType performanceType(Long id, String name, int score) {
        PerformanceType type = new PerformanceType();
        type.setId(id);
        type.setSiteId(INIT_SITE_ID);
        type.setName(name);
        type.setScore(score);
        type.setOrder(id);
        return type;
    }

    private void initLucene() {
        luceneGenerator.reindex(INIT_SITE_ID, INIT_USER_ID, "init database");
    }

    /**
     * 引导管理员口令（{@code password}）的 PBKDF2 摘要——与原引导脚本一致；
     * {@code pwd_must_change_} 置位，安装步骤设置真实凭据时清除
     */
    private static final String ADMIN_PASSWORD_HASH =
            "{pbkdf2}0Knzbc+J3Fkl5/mlJBrwLFOUwOvoBANcrfCgVvrjCSIBjnazXupYVw==";

    // ----- 设置列 JSON 常量：与原引导脚本落库字节一致（实体以原始 JSON 字符串持有设置列）；docTypes 自 DOC 导入内置 WPS 文字起多了 wps -----

    private static final String CONFIG_UPLOAD_SETTINGS = "{\"imageTypes\":\"jpg,jpeg,png,gif\","
            + "\"videoTypes\":\"mp4,m3u8\",\"docTypes\":\"pdf,doc,docx,wps,xls,xlsx,ppt,pptx\","
            + "\"fileTypes\":\"zip,7z,gz,bz2,iso,rar,pdf,doc,docx,xls,xlsx,ppt,pptx\",\"imageLimit\":0,"
            + "\"videoLimit\":0,\"docLimit\":0,\"fileLimit\":0,\"imageMaxWidth\":1920,\"imageMaxHeight\":0,"
            + "\"videoLimitByte\":0,\"imageLimitByte\":0,\"docLimitByte\":0,\"fileLimitByte\":0,"
            + "\"docInputAccept\":\".zip,.7z,.gz,.bz2,.iso,.rar,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx\","
            + "\"videoInputAccept\":\".mp4,.m3u8\","
            + "\"fileInputAccept\":\".zip,.7z,.gz,.bz2,.iso,.rar,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx\","
            + "\"imageInputAccept\":\".jpg,.jpeg,.png,.gif\"}";

    private static final String CONFIG_REGISTER_SETTINGS = "{\"enabled\":false,\"verifyMode\":1,"
            + "\"usernameMinLength\":4,\"usernameMaxLength\":12,"
            + "\"usernameRegex\":\"^[0-9a-zA-Z\\\\u4e00-\\\\u9fa5\\\\.\\\\-_]+$\","
            + "\"avatar\":\"/uploads/avatar/default/default.png\",\"smallAvatarSize\":80,"
            + "\"mediumAvatarSize\":240,\"largeAvatarSize\":960}";

    private static final String SITE_EDITOR_SETTINGS = "{\"typesetting\":{\"fontFamily\":\"\","
            + "\"fontSize\":\"\",\"lineHeight\":\"\",\"textIndent\":true,\"imageCenterAlign\":true,"
            + "\"tableFullWidth\":true,\"emptyLine\":\"one\",\"halfWidthCharConversion\":true}}";

    private static final String SITE_WATERMARK_SETTINGS = "{\"enabled\":false,\"overlay\":null,"
            + "\"position\":9,\"dissolve\":50,\"minWidth\":300,\"minHeight\":300}";

    private static final String SITE_HTML_SETTINGS = "{\"enabled\":false,\"auto\":true,\"listPages\":1,"
            + "\"channel\":\"/channels/{channel_slug}/index\",\"article\":\"/articles/{article_id}\","
            + "\"enabledAndAuto\":false}";
}
