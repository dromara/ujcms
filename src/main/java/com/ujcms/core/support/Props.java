package com.ujcms.core.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UJCMS 系统配置。部分属性使用静态属性，方便实体类调用。
 *
 * @author PONY
 */
@ConfigurationProperties("ujcms")
public class Props {
    private String version = "none";
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
     * Lucene 索引文件保存目录
     */
    private String lucenePath = "/WEB-INF/lucene";
    /**
     * 直接将上传文件定位到真实路径。通常为 {@code /uploads}
     * <p>
     * 开发环境下，上传的图片无法立即访问，需要等待1至5秒不等。可能是因为上传后，开发工具需要同步到实际 tomcat 的运行路径下。
     */
    @Nullable
    private String uploadsLocation;
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
     * 是否演示网站。演示网站的 test 用户只能执行 GET 请求。
     */
    private boolean demo = false;

    /**
     * ElasticSearch Article 索引名
     */
    public String getEsArticle() {
        return getEsPrefix() + "-article";
    }

    //以下非final常量可以被配置文件覆盖

    /**
     * 模板存储路径
     */
    public static String TEMPLATE_PATH = "/templates";
    /**
     * 模板访问路径
     */
    public static String TEMPLATE_URL = "/templates";
    public static List<String> EP_EXCLUDES = new ArrayList<>(
            Arrays.asList("site:list", "generator:fulltext:reindexSite", "machine:code", "machine:license"));
    public static boolean EP_DISPLAY = true;
    public static int EP_RANK = 0;
    public static boolean EP_ACTIVATED = false;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    public String getTemplatePath() {
        return Props.TEMPLATE_PATH;
    }

    public void setTemplatePath(String templatePath) {
        Props.TEMPLATE_PATH = templatePath;
    }

    public String getTemplateUrl() {
        return Props.TEMPLATE_URL;
    }

    public void setTemplateUrl(String templateUrl) {
        Props.TEMPLATE_URL = templateUrl;
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

    public String getLucenePath() {
        return lucenePath;
    }

    public void setLucenePath(String lucenePath) {
        this.lucenePath = lucenePath;
    }

    @Nullable
    public String getUploadsLocation() {
        return uploadsLocation;
    }

    public void setUploadsLocation(@Nullable String uploadsLocation) {
        this.uploadsLocation = uploadsLocation;
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

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public boolean isEpDisplay() {
        return EP_DISPLAY;
    }

    public void setEpDisplay(boolean epDisplay) {
        EP_DISPLAY = epDisplay;
    }
}
