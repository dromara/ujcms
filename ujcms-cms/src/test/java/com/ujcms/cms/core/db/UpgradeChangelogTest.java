package com.ujcms.cms.core.db;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变更日志的两条路径（H2 内存库）：
 * <ul>
 *   <li>升级：先按 12.x 的变更日志建库并写入存量行（设置、用户手机/邮箱、留言联系方式、/{siteId}/{theme} 形式的主题名），
 *       再跑完整主变更日志——存量数据必须原样保留，列类型改成大文本，主题名改成裸名；</li>
 *   <li>全新建库：空库跑完整主变更日志走删列重建，升级变更集标记跳过；</li>
 *   <li>13.0 发布后新装、之后再升级到本版本的库：删列重建已执行过，升级变更集靠 changeSetExecuted 标记跳过，不碰列。</li>
 * </ul>
 * 13.0 的删列重建变更集已经发布，这里顺带守住“只加前置条件、不改 changes”的约束——校验和一变存量库就起不来。
 */
class UpgradeChangelogTest {
    private static final String MASTER = "db/changelog/db.changelog-master.yaml";
    /** 与主变更日志同目录，include 的相对路径才能落到类路径里的同名文件，DATABASECHANGELOG 里记的文件名也才一致 */
    private static final String PRE_13_MASTER = "db/changelog/db.changelog-test-pre13.yaml";

    private static final String[] CONFIG_SETTINGS = {"upload_settings_", "grey_settings_", "security_settings_",
            "register_settings_", "sms_settings_", "email_settings_", "upload_storage_settings_",
            "html_storage_settings_", "template_storage_settings_"};
    private static final String[] CONFIG_UPGRADE_IDS = {
            "13.0-upgrade-config-upload-settings-to-mediumtext", "13.0-upgrade-config-grey-settings-to-mediumtext",
            "13.0-upgrade-config-security-settings-to-mediumtext", "13.0-upgrade-config-register-settings-to-mediumtext",
            "13.0-upgrade-config-sms-settings-to-mediumtext", "13.0-upgrade-config-email-settings-to-mediumtext",
            "13.0-upgrade-config-upload-storage-settings-to-mediumtext",
            "13.0-upgrade-config-html-storage-settings-to-mediumtext",
            "13.0-upgrade-config-template-storage-settings-to-mediumtext"};
    private static final String[] MESSAGE_BOARD_UPGRADE_IDS = {
            "13.0-upgrade-message-board-contact-to-mediumtext", "13.0-upgrade-message-board-phone-to-mediumtext",
            "13.0-upgrade-message-board-email-to-mediumtext", "13.0-upgrade-message-board-address-to-mediumtext"};
    private static final String[] USER_UPGRADE_IDS = {
            "13.0-upgrade-user-mobile-to-mediumtext", "13.0-upgrade-user-email-to-mediumtext",
            "13.0-upgrade-user-pii-index-columns", "13.0-upgrade-user-mobile-index-unique",
            "13.0-upgrade-user-email-index-unique"};
    private static final String CLOB = "CHARACTER LARGE OBJECT";

    @TempDir
    Path tempDir;

    @Test
    void upgradeFromPre13KeepsExistingData() throws Exception {
        Path pre13 = writePre13Master(tempDir);
        ResourceAccessor accessor = new CompositeResourceAccessor(new DirectoryResourceAccessor(tempDir),
                new ClassLoaderResourceAccessor());
        try (Connection conn = openH2("upgrade")) {
            update(conn, PRE_13_MASTER, accessor);
            assertTrue(ranChangeSets(conn) > 200, "12.x 的变更集应已全部执行");
            assertEquals("CHARACTER VARYING", columnType(conn, "ujcms_config", "upload_settings_"), "12.x 里还是 VARCHAR");

            // 存量数据（外键在 H2 里关掉：只关心列搬移，不建完整的组织/模型/留言类型图谱）
            try (Statement st = conn.createStatement()) {
                st.execute("set referential_integrity false");
                st.execute("insert into ujcms_config (id_, default_site_id_, upload_settings_, grey_settings_, "
                        + "security_settings_, register_settings_, sms_settings_, email_settings_, "
                        + "upload_storage_settings_, html_storage_settings_, template_storage_settings_) values "
                        + "(1, 1, '{\"k\":\"upload\"}', '{\"k\":\"grey\"}', '{\"k\":\"security\"}', '{\"k\":\"register\"}', "
                        + "'{\"k\":\"sms\"}', '{\"k\":\"email\"}', '{\"k\":\"upload_storage\"}', '{\"k\":\"html_storage\"}', "
                        + "'{\"k\":\"template_storage\"}')");
                st.execute("insert into ujcms_site (id_, org_id_, model_id_, name_, protocol_, domain_, theme_, "
                        + "mobile_theme_, max_date_) values (1, 1, 1, 'UJCMS', 'https', 'www.example.test', "
                        + "'/1/default', '/1/mobile', now())");
                st.execute("insert into ujcms_user (id_, group_id_, org_id_, username_, password_modified_, mobile_, "
                        + "email_) values (1, 1, 1, 'admin', now(), '13800000001', 'admin@example.test')");
                st.execute("insert into ujcms_user (id_, group_id_, org_id_, username_, password_modified_) "
                        + "values (2, 1, 1, 'nobody', now())");
                st.execute("insert into ujcms_message_board (id_, site_id_, type_id_, user_id_, title_, created_, ip_, "
                        + "contact_, phone_, email_, address_) values (1, 1, 1, 1, '留言', now(), '127.0.0.1', "
                        + "'联系人', '010-12345678', 'mb@example.test', '地址')");
                st.execute("set referential_integrity true");
            }

            update(conn, MASTER, accessor);

            // 设置列：值原样保留，类型已是大文本，临时列不残留
            for (String column : CONFIG_SETTINGS) {
                String key = column.substring(0, column.length() - "_settings_".length());
                assertEquals("{\"k\":\"" + key + "\"}", scalar(conn, "select " + column + " from ujcms_config where id_ = 1"),
                        column + " 的存量值丢了");
                assertEquals(CLOB, columnType(conn, "ujcms_config", column), column);
                assertNull(columnType(conn, "ujcms_config", column.replace("_settings_", "_settings_tmp_")), "临时列残留");
            }
            assertEquals("installed", scalar(conn, "select install_state_ from ujcms_config where id_ = 1"),
                    "存量库必须视同已安装，否则前台显示“系统尚未安装”");

            // 主题名：/{siteId}/{theme} → 裸名
            assertEquals("default", scalar(conn, "select theme_ from ujcms_site where id_ = 1"));
            assertEquals("mobile", scalar(conn, "select mobile_theme_ from ujcms_site where id_ = 1"));

            // 用户手机/邮箱（升级后仍是明文，盲索引列为空，由 rebuild-pii-index 回填）
            assertEquals("13800000001", scalar(conn, "select mobile_ from ujcms_user where id_ = 1"));
            assertEquals("admin@example.test", scalar(conn, "select email_ from ujcms_user where id_ = 1"));
            assertNull(scalar(conn, "select mobile_ from ujcms_user where id_ = 2"));
            assertEquals(CLOB, columnType(conn, "ujcms_user", "mobile_"));
            assertEquals(CLOB, columnType(conn, "ujcms_user", "email_"));
            assertEquals("CHARACTER VARYING", columnType(conn, "ujcms_user", "mobile_index_"));
            assertEquals("CHARACTER VARYING", columnType(conn, "ujcms_user", "email_index_"));
            assertEquals("1", scalar(conn, "select count(*) from information_schema.indexes where upper(table_name) = 'UJCMS_USER' "
                    + "and upper(index_name) = 'UK_USER_MOBILE_INDEX'"));
            assertEquals("1", scalar(conn, "select count(*) from information_schema.indexes where upper(table_name) = 'UJCMS_USER' "
                    + "and upper(index_name) = 'UK_USER_EMAIL_INDEX'"));

            // 留言联系方式
            assertEquals("联系人", scalar(conn, "select contact_ from ujcms_message_board where id_ = 1"));
            assertEquals("010-12345678", scalar(conn, "select phone_ from ujcms_message_board where id_ = 1"));
            assertEquals("mb@example.test", scalar(conn, "select email_ from ujcms_message_board where id_ = 1"));
            assertEquals("地址", scalar(conn, "select address_ from ujcms_message_board where id_ = 1"));
            assertEquals(CLOB, columnType(conn, "ujcms_message_board", "contact_"));

            // 走的是升级路径：删列重建标记跳过，升级变更集执行
            assertExecType(conn, "MARK_RAN", "13.0-config-settings-to-mediumtext",
                    "13.0-message-board-contact-to-mediumtext", "13.0-user-pii-encryption");
            assertExecType(conn, "EXECUTED", CONFIG_UPGRADE_IDS);
            assertExecType(conn, "EXECUTED", MESSAGE_BOARD_UPGRADE_IDS);
            assertExecType(conn, "EXECUTED", USER_UPGRADE_IDS);
            assertExecType(conn, "EXECUTED", "13.0-upgrade-site-theme-bare-name");

            // 再跑一遍：无事可做（校验和与前置条件都稳定）
            int ran = ranChangeSets(conn);
            update(conn, MASTER, accessor);
            assertEquals(ran, ranChangeSets(conn));
        }
    }

    @Test
    void freshDatabaseTakesTheRebuildPath() throws Exception {
        ResourceAccessor accessor = new ClassLoaderResourceAccessor();
        try (Connection conn = openH2("fresh")) {
            update(conn, MASTER, accessor);
            assertExecType(conn, "EXECUTED", "13.0-config-settings-to-mediumtext",
                    "13.0-message-board-contact-to-mediumtext", "13.0-user-pii-encryption",
                    "13.0-upgrade-site-theme-bare-name");
            assertExecType(conn, "MARK_RAN", CONFIG_UPGRADE_IDS);
            assertExecType(conn, "MARK_RAN", MESSAGE_BOARD_UPGRADE_IDS);
            assertExecType(conn, "MARK_RAN", USER_UPGRADE_IDS);
            for (String column : CONFIG_SETTINGS) {
                assertEquals(CLOB, columnType(conn, "ujcms_config", column), column);
            }
            assertEquals(CLOB, columnType(conn, "ujcms_user", "mobile_"));
            assertEquals(CLOB, columnType(conn, "ujcms_message_board", "contact_"));
        }
    }

    /**
     * 13.0 发布后新装的库（删列重建已执行、还没有升级变更集的记录），升级到本版本：表里有数据，
     * 升级变更集只能靠 changeSetExecuted 判定跳过，不得再搬一次列。
     */
    @Test
    void databaseInstalledOn13ThenUpgradedSkipsTheUpgradeChangeSets() throws Exception {
        ResourceAccessor accessor = new ClassLoaderResourceAccessor();
        try (Connection conn = openH2("installed13")) {
            update(conn, MASTER, accessor);
            try (Statement st = conn.createStatement()) {
                st.execute("delete from databasechangelog where id like '13.0-upgrade-%'");
                st.execute("set referential_integrity false");
                st.execute("insert into ujcms_config (id_, default_site_id_, upload_settings_, install_state_) "
                        + "values (1, 1, '{\"k\":\"upload\"}', 'installed')");
                st.execute("insert into ujcms_user (id_, group_id_, org_id_, username_, password_modified_, mobile_, "
                        + "mobile_index_) values (1, 1, 1, 'admin', now(), 'cipher', 'index')");
                st.execute("insert into ujcms_message_board (id_, site_id_, type_id_, user_id_, title_, created_, ip_, "
                        + "contact_) values (1, 1, 1, 1, '留言', now(), '127.0.0.1', '联系人')");
                st.execute("set referential_integrity true");
            }

            update(conn, MASTER, accessor);

            assertExecType(conn, "MARK_RAN", CONFIG_UPGRADE_IDS);
            assertExecType(conn, "MARK_RAN", MESSAGE_BOARD_UPGRADE_IDS);
            assertExecType(conn, "MARK_RAN", USER_UPGRADE_IDS);
            assertEquals("{\"k\":\"upload\"}", scalar(conn, "select upload_settings_ from ujcms_config where id_ = 1"));
            assertEquals("cipher", scalar(conn, "select mobile_ from ujcms_user where id_ = 1"));
            assertEquals("index", scalar(conn, "select mobile_index_ from ujcms_user where id_ = 1"));
            assertEquals("联系人", scalar(conn, "select contact_ from ujcms_message_board where id_ = 1"));
            assertNull(columnType(conn, "ujcms_config", "upload_settings_tmp_"));
        }
    }

    // ---- 工具 ----

    private static Connection openH2(String name) throws SQLException {
        // 不加 DATABASE_TO_LOWER：Liquibase 按大写名探测 DATABASECHANGELOG，小写存储时探测不到又重复建表
        return DriverManager.getConnection("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static void update(Connection conn, String changeLog, ResourceAccessor accessor) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
        // 不 close：Liquibase.close 会把底层 JDBC 连接一起关掉
        Liquibase liquibase = new Liquibase(changeLog, accessor, database);
        liquibase.update("");
    }

    /** 主变更日志里 13.0 之前的 include（10.0 整库、Quartz、12.x）另存一份，模拟 12.x 的库 */
    private static Path writePre13Master(Path dir) throws Exception {
        String master;
        try (InputStream in = Objects.requireNonNull(
                UpgradeChangelogTest.class.getClassLoader().getResourceAsStream(MASTER), MASTER)) {
            master = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> lines = List.of(master.split("\n"));
        List<String> kept = new ArrayList<>();
        int includes = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().equals("- include:")) {
                String file = lines.get(i + 1).trim();
                if (file.contains("db.changelog-13.")) {
                    break;
                }
                kept.add(line);
                kept.add(lines.get(i + 1));
                kept.add(lines.get(i + 2));
                i += 2;
                includes++;
                continue;
            }
            kept.add(line);
        }
        assertTrue(includes >= 20, "13.0 之前的 include 应有 10.0 整库 + Quartz + 12.x 各文件，实际 " + includes);
        assertTrue(kept.stream().anyMatch(l -> l.contains("db.changelog-12.6-geo.yaml")), "12.x 的最后一个文件应在内");
        Path file = dir.resolve(PRE_13_MASTER);
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", kept) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    private static String scalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "无结果: " + sql);
            return rs.getString(1);
        }
    }

    private static String columnType(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "select data_type from information_schema.columns where upper(table_name) = '" + table.toUpperCase()
                        + "' and upper(column_name) = '" + column.toUpperCase() + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int ranChangeSets(Connection conn) throws SQLException {
        return Integer.parseInt(scalar(conn, "select count(*) from databasechangelog"));
    }

    private static void assertExecType(Connection conn, String expected, String... ids) throws SQLException {
        for (String id : ids) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                    "select exectype from databasechangelog where id = '" + id + "'")) {
                assertTrue(rs.next(), "变更集没有记录: " + id);
                assertEquals(expected, rs.getString(1), id);
            }
        }
    }
}
