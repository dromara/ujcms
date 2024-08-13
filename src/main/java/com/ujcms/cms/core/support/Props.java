package com.ujcms.cms.core.support;

import com.ujcms.commons.security.Secures;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * UJCMS 系统配置。部分属性使用静态属性，方便实体类调用。
 *
 * @author PONY
 */
@ConfigurationProperties("ujcms")
public class Props {
    public Props() {
        initClientSm2Key();
    }

    private String version = "none";
    /**
     * 雪花算法主键：数据中心ID。取值范围 0-31。为 -1 时，根据网卡mac地址和pid(进程ID)计算出一个相对随机的数值，一般情况下不会冲突。
     */
    private int datacenterId = -1;
    /**
     * 雪花算法主键：工作机器ID。取值范围 0-31。为 -1 时，根据网卡mac地址和pid(进程ID)计算出一个相对随机的数值，一般情况下不会冲突。
     */
    private int workerId = -1;
    /**
     * 序列默认缓存数。如果数据库和程序均未指定缓存大小，则使用该值。JPA Table主键策略的默认缓存值也是 50
     */
    private int sequenceCacheSize = 50;
    /**
     * 是否执行数据初始化脚本
     */
    private boolean dataSqlEnabled = true;
    /**
     * 数据初始化脚本的数据库类型
     */
    private String dataSqlPlatform = "mysql";
    /**
     * 是否允许访问JSP。允许JSP访问会带来一定的安全风险，比如攻击者通过上传JSP文件，获得webshell。
     * 若允许，所有JSP文件必须放在 {@code /jsp} 目录下，但访问地址不用加 {@code /jsp} 前缀。
     * 比如： {@code /jsp/example.jsp} 文件的访问地址依然为 {@code /example.jsp}
     */
    private boolean jspAllowed = false;
    /**
     * ElasticSearch 索引前缀
     */
    private String esPrefix = "ujcms";
    /**
     * IP地址库文件
     */
    private String ip2regionPath = "classpath:ip2region.xdb";
    /**
     * lucene 分词器。支持`jcscg` `ik`。默认使用`jcseg`。
     * `jcseg`支持最小粒度分词（单字），避免出现搜索某个字时无法搜索到结果。但在单篇文章内容过多时（如十万字），会出现错误：`org.apache.lucene.search.highlight.InvalidTokenOffsetsException: Token 形 exceeds length of provided text sized 15`
     * `ik`分词器，不支持最小粒度分词，但性能和稳定性更好。
     */
    private String luceneAnalyzer = "jcseg";
    /**
     * Lucene 索引文件保存目录
     */
    private String lucenePath = "/WEB-INF/lucene";
    /**
     * 备份保存目录
     */
    private String backupPath = "/WEB-INF/backup";
    /**
     * 数据库类型。支持 mysql、postgresql、kingbase、opengauss
     */
    private String databaseType = "";
    /**
     * 数据库的 bin 目录，用于定位备份和恢复命令。如果备份恢复命令加入环境变量path，就必须指定该项。
     */
    private String databaseBinPath = "";
    /**
     * 使用URL作为模板加载地址。使用对象存储保存模板时，需使用这种方式加载模板。
     */
    @Nullable
    private String templateLoaderUrl;
    /**
     * 直接将上传文件定位到真实路径。通常为 {@code /uploads}
     * <p>
     * 开发环境下，上传的图片无法立即访问，需要等待1至5秒不等。可能是因为上传后，开发工具需要同步到实际 tomcat 的运行路径下。
     */
    @Nullable
    private String uploadsLocation;
    /**
     * 存储路径允许的文件前缀。多个路径用逗号分开。
     */
    private String storageFilePrefix = "";
    /**
     * 文件上传后缀黑名单
     */
    private String uploadsExtensionBlacklist = "exe,com,bat,jsp,jspx,asp,aspx,php,html,htm,xhtml,xml,shtml,shtm";
    /**
     * 文件管理后缀黑名单
     */
    private String filesExtensionBlacklist = "exe,com,bat,jsp,jspx,asp,aspx,php";
    /**
     * 是否开启文件管理功能
     */
    private boolean filesManagementEnabled = true;
    /**
     * 是否支持静态页目录访问形式。使用了 nginx 处理静态页则该项可以关闭。
     * 如访问 /foo/ 则显示 /foo/index.html 内容。
     * 并且在 /foo/index.html 文件存在的情况下，将 /foo 重定向至 /foo/
     */
    private boolean isFileToDir = true;
    /**
     * 是否将访问目录的 url 重定向至访问文件。例如：/foo/ 重定向至 /foo
     */
    private boolean isDirToFile = true;
    /**
     * 下载认证密钥。不应少于32个随机字符。
     */
    private String downloadSecret = Secures.randomAlphanumeric(32);
    /**
     * 客户端SM2公钥。使用QD值，HEX编码。
     */
    private String clientSm2PublicKey = "";
    /**
     * 客户端SM2私钥。使用QD值，HEX编码。
     */
    private String clientSm2PrivateKey = "";
    /**
     * 密码胡椒。不应少于32个随机字符。此值不可改动，否则用户密码验证将全部失效。
     * 密码胡椒将加在用户实际密码的前面，防止黑客在获取数据库数据后，对密码进行破解。
     * 如pepper为 "123"，用户密码是"abc"，则使用"123abc"作为密码进行加密。
     */
    @Nullable
    private String passwordPepper;
    /**
     * 是否开启数据迁移
     */
    private boolean dataMigrationEnabled = false;
    /**
     * 是否演示网站。演示网站的 test 用户只能执行 GET 请求。
     */
    private boolean demo = false;

    /**
     * ElasticSearch Article 索引名
     */
    public String getEsArticle() {
        return getEsPrefix() + "-article";
    }

    /**
     * 存储点前缀列表
     */
    public List<String> getStorageFilePrefixList() {
        return Arrays.asList(storageFilePrefix.split(","));
    }

    /**
     * 文件上传后缀排除列表
     */
    public List<String> getUploadsExtensionExcludes() {
        return Arrays.asList(uploadsExtensionBlacklist.split(","));
    }

    /**
     * 文件管理后缀排除列表
     */
    public List<String> getFilesExtensionExcludes() {
        return Arrays.asList(filesExtensionBlacklist.split(","));
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getDatacenterId() {
        return datacenterId;
    }

    public void setDatacenterId(int datacenterId) {
        this.datacenterId = datacenterId;
    }

    public int getWorkerId() {
        return workerId;
    }

    public void setWorkerId(int workerId) {
        this.workerId = workerId;
    }

    public int getSequenceCacheSize() {
        return sequenceCacheSize;
    }

    public void setSequenceCacheSize(int sequenceCacheSize) {
        this.sequenceCacheSize = sequenceCacheSize;
    }

    public boolean isDataSqlEnabled() {
        return dataSqlEnabled;
    }

    public void setDataSqlEnabled(boolean dataSqlEnabled) {
        this.dataSqlEnabled = dataSqlEnabled;
    }

    public String getDataSqlPlatform() {
        return dataSqlPlatform;
    }

    public void setDataSqlPlatform(String dataSqlPlatform) {
        this.dataSqlPlatform = dataSqlPlatform;
    }

    public boolean isJspAllowed() {
        return jspAllowed;
    }

    public void setJspAllowed(boolean jspAllowed) {
        this.jspAllowed = jspAllowed;
    }

    public String getEsPrefix() {
        return esPrefix;
    }

    public void setEsPrefix(String esPrefix) {
        this.esPrefix = esPrefix;
    }

    public String getIp2regionPath() {
        return ip2regionPath;
    }

    public void setIp2regionPath(String ip2regionPath) {
        this.ip2regionPath = ip2regionPath;
    }

    public String getLuceneAnalyzer() {
        return luceneAnalyzer;
    }

    public void setLuceneAnalyzer(String luceneAnalyzer) {
        this.luceneAnalyzer = luceneAnalyzer;
    }

    public String getLucenePath() {
        return lucenePath;
    }

    public void setLucenePath(String lucenePath) {
        this.lucenePath = lucenePath;
    }

    public String getBackupPath() {
        return backupPath;
    }

    public void setBackupPath(String backupPath) {
        this.backupPath = backupPath;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }

    public String getDatabaseBinPath() {
        return databaseBinPath;
    }

    public void setDatabaseBinPath(String databaseBinPath) {
        this.databaseBinPath = databaseBinPath;
    }

    @Nullable
    public String getTemplateLoaderUrl() {
        return templateLoaderUrl;
    }

    public void setTemplateLoaderUrl(@Nullable String templateLoaderUrl) {
        this.templateLoaderUrl = templateLoaderUrl;
    }

    @Nullable
    public String getUploadsLocation() {
        return uploadsLocation;
    }

    public void setUploadsLocation(@Nullable String uploadsLocation) {
        this.uploadsLocation = uploadsLocation;
    }

    public String getStorageFilePrefix() {
        return storageFilePrefix;
    }

    public void setStorageFilePrefix(String storageFilePrefix) {
        this.storageFilePrefix = storageFilePrefix;
    }

    public String getUploadsExtensionBlacklist() {
        return uploadsExtensionBlacklist;
    }

    public void setUploadsExtensionBlacklist(String uploadsExtensionBlacklist) {
        this.uploadsExtensionBlacklist = uploadsExtensionBlacklist;
    }

    public String getFilesExtensionBlacklist() {
        return filesExtensionBlacklist;
    }

    public void setFilesExtensionBlacklist(String filesExtensionBlacklist) {
        this.filesExtensionBlacklist = filesExtensionBlacklist;
    }

    public boolean isFilesManagementEnabled() {
        return filesManagementEnabled;
    }

    public void setFilesManagementEnabled(boolean filesManagementEnabled) {
        this.filesManagementEnabled = filesManagementEnabled;
    }

    public boolean isFileToDir() {
        return isFileToDir;
    }

    public void setFileToDir(boolean fileToDir) {
        isFileToDir = fileToDir;
    }

    public boolean isDirToFile() {
        return isDirToFile;
    }

    public void setDirToFile(boolean dirToFile) {
        isDirToFile = dirToFile;
    }

    public String getDownloadSecret() {
        return downloadSecret;
    }

    public void setDownloadSecret(String downloadSecret) {
        this.downloadSecret = downloadSecret;
    }

    public String getClientSm2PublicKey() {
        return clientSm2PublicKey;
    }

    public void setClientSm2PublicKey(String clientSm2PublicKey) {
        this.clientSm2PublicKey = clientSm2PublicKey;
    }

    public String getClientSm2PrivateKey() {
        return clientSm2PrivateKey;
    }

    public void setClientSm2PrivateKey(String clientSm2PrivateKey) {
        this.clientSm2PrivateKey = clientSm2PrivateKey;
    }

    @Nullable
    public String getPasswordPepper() {
        return passwordPepper;
    }

    public void setPasswordPepper(@Nullable String passwordPepper) {
        this.passwordPepper = passwordPepper;
    }

    public boolean isDataMigrationEnabled() {
        return dataMigrationEnabled;
    }

    public void setDataMigrationEnabled(boolean dataMigrationEnabled) {
        this.dataMigrationEnabled = dataMigrationEnabled;
    }

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public void setEpDisplay(boolean epDisplay) {
        StaticProps.setEpDisplay(epDisplay);
    }

    private void initClientSm2Key() {
        Secures.Pair keyPair = Secures.generateSm2QdKeyPair();
        this.clientSm2PublicKey = keyPair.getPublicKey();
        this.clientSm2PrivateKey = keyPair.getPrivateKey();
    }
}
