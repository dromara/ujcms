package com.ujcms.cms.core.support;

import com.ujcms.common.security.Secures;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

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
    public Props() {
        initClientSm2Key();
    }

    private String version = "none";
    /**
     * 雪花算法主键：数据中心ID。取值范围 0-31，需与 workerId 同时设置。为 -1 时，根据网卡 MAC 地址、主机名和进程ID 自动推算；
     * 容器/K8s 集群中 MAC 与进程ID 可能完全相同，集群部署时应显式设置。
     */
    private int datacenterId = -1;
    /**
     * 雪花算法主键：工作机器ID。取值范围 0-31，需与 datacenterId 同时设置。为 -1 时，根据网卡 MAC 地址、主机名和进程ID 自动推算；
     * 容器/K8s 集群中 MAC 与进程ID 可能完全相同，集群部署时应显式设置。
     */
    private int workerId = -1;
    /**
     * 序列默认缓存数。如果数据库和程序均未指定缓存大小，则使用该值。JPA Table主键策略的默认缓存值也是 50
     */
    private int sequenceCacheSize = 50;
    /**
     * 是否执行系统核心引导（空库首次启动时以固定 ID 写入系统骨架行，install-flow §4）
     */
    private boolean bootstrapEnabled = true;
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
     * IPv6 地址库文件。文件不存在或不可用时 IPv6 归属地查询返回 UNKNOWN，不影响其他功能。
     * 可指向 classpath 或本机文件，如 {@code file:/opt/ujcms/ip2region_v6.xdb}（约 37 MB，启动时全部载入内存）。
     */
    private String ip2regionV6Path = "classpath:ip2region_v6.xdb";
    /**
     * IP代理深度。用于防止X-Forwarded-For头部伪造攻击。
     */
    private int ipProxyDepth = 0;
    /**
     * IPv6 限流分组前缀长度（位）。登录失败次数、短信/邮件发送次数、匿名互动限流按此前缀对 IPv6 客户端分组计数。
     * 默认 64：隐私扩展地址只在后 64 位轮换，按单个地址计数会被绕过；128 表示按单个地址。IPv4 不受影响。
     */
    private int ipv6ThrottlePrefixLength = 64;
    /**
     * 匿名互动（投票/调查/文章顶踩）IP 限流：单个 IP 每分钟最多互动次数。{@code 0} 表示关闭（默认）。
     * 与一人一票去重（Cookie/IP/会员）是不同职责，此项仅限制请求速率以防脚本刷量。
     * 注意：CGNAT / 共享出口环境下 IP 限流可能误伤正常用户，需酌情调大或保持关闭。建议值 30。
     */
    private int interactionMaxPerMinute = 0;
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
     * 敏感数据加密密钥。短信/邮件/对象存储等凭据（及其它登记加密的字段）入库前用国密 SM4-GCM 加密，
     * 密钥与数据库分离。必须为 16 个字符。留空则使用内置默认密钥——默认值即可满足一般需求，
     * 仅在安全要求较高时才需自定义。
     */
    private String dataSecretKey = "";
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
     * 安装清单（install-flow §7）。{@code ujcms.install.auto=true} 时，启动末位
     * （构成注册表首个快照之后）在安装状态非 INSTALLED 的库上无人值守跑完安装引擎：
     * 包库目录摄取 → 站点 1 配置 → 管理员凭据 → 各站点主题安装与种子导入 → 完成翻转。
     * 仅首装路径（CORE_READY）运行，绝不在已安装库上重跑（首装初始化例外规则）。
     * 取代已删除的 {@code ujcms.init-*} 五键（D7，无别名兼容层）。
     */
    private Install install = new Install();

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
        Frontends.setVersion(version);
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

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
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

    public String getIp2regionV6Path() {
        return ip2regionV6Path;
    }

    public void setIp2regionV6Path(String ip2regionV6Path) {
        this.ip2regionV6Path = ip2regionV6Path;
    }

    public int getIpProxyDepth() {
        return ipProxyDepth;
    }

    public void setIpProxyDepth(int ipProxyDepth) {
        this.ipProxyDepth = ipProxyDepth;
    }

    public int getIpv6ThrottlePrefixLength() {
        return ipv6ThrottlePrefixLength;
    }

    public void setIpv6ThrottlePrefixLength(int ipv6ThrottlePrefixLength) {
        this.ipv6ThrottlePrefixLength = ipv6ThrottlePrefixLength;
    }

    public int getInteractionMaxPerMinute() {
        return interactionMaxPerMinute;
    }

    public void setInteractionMaxPerMinute(int interactionMaxPerMinute) {
        this.interactionMaxPerMinute = interactionMaxPerMinute;
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

    public String getDataSecretKey() {
        return dataSecretKey;
    }

    /**
     * 绑定 {@code ujcms.data-secret-key} 时校验并下发到 {@link StaticProps}（供领域层/TypeHandler 静态读取）。
     * 留空则保留 {@link StaticProps#DEFAULT_DATA_SECRET_KEY} 内置默认密钥（默认值即可满足一般需求）；
     * 显式配置但长度非 16 时直接报错（fail-loud），避免运行期加解密失败。
     */
    public void setDataSecretKey(String dataSecretKey) {
        this.dataSecretKey = dataSecretKey;
        if (StringUtils.isEmpty(dataSecretKey)) {
            return;
        }
        if (dataSecretKey.length() != StaticProps.DATA_SECRET_KEY_LENGTH) {
            throw new IllegalStateException("ujcms.data-secret-key 必须为 "
                    + StaticProps.DATA_SECRET_KEY_LENGTH + " 个字符（SM4-128），当前长度："
                    + dataSecretKey.length());
        }
        StaticProps.setDataSecretKey(dataSecretKey);
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

    public Install getInstall() {
        return install;
    }

    public void setInstall(Install install) {
        this.install = install;
    }

    private void initClientSm2Key() {
        Secures.Pair keyPair = Secures.generateSm2QdKeyPair();
        this.clientSm2PublicKey = keyPair.getPublicKey();
        this.clientSm2PrivateKey = keyPair.getPrivateKey();
    }

    /**
     * 安装清单（{@code ujcms.install.*}，install-flow §7）。全部字段可缺省——缺省即保留
     * 引导骨架的当前值（站点 1 / 管理员为固定 ID 引导行，安装只<b>配置</b>、绝不创建，D4）。
     * {@code sites} 首条配置站点 1；此后条目走 T2 建站复制路径，社区版 fail-loud（D11）。
     * {@code users:} 追加用户（core，各版本可用）；{@code demo:} 演示层（P5 §8）驱动真实
     * Flowable 引擎，商业 T1 类路径缺席时跳过并落报告行，绝不中止安装。
     * 多语言变体链接（{@code langOf}，Q3 → D44）：条目以 {@code lang}+{@code langOf}
     * （站点名引用）挂入既有站点的语言组，{@code subDir} 支撑同域名子目录拓扑。
     */
    public static class Install {
        /**
         * 是否无人值守自动安装。开发/测试 Profile 与 Docker 演示置 {@code true}；
         * 交互安装（控制台向导）保持 {@code false}。
         */
        private boolean auto = false;
        /**
         * 预设安装令牌。非空时引导不再随机生成令牌（也不在日志打印明文），直接使用此值的
         * SM3 摘要——容器内交互安装可经环境变量注入（Q4/D18）。自动安装为进程内调用，不需令牌。
         */
        private String token = "";
        /**
         * 主题包库目录摄取（§7：Docker「本地主题目录」的正名归宿）：安装引擎起步时将该目录下
         * 的 {@code *.zip} 逐个登记入主题包库（按 sha256 幂等），随后站点主题的 {@code library}
         * 来源即可按名解析。容器场景挂载一个 ZIP 目录即可离线供包。缺省不摄取。
         */
        @Nullable
        private String libraryDir;
        /**
         * 管理员凭据（安装引擎 {@code configureAdmin}）。
         */
        private Admin admin = new Admin();
        /**
         * 站点清单。首条配置引导站点 1（D4——名称/协议/域名/端口，提供域名时端口缺省表示
         * 清除 80/443）；此后条目经 T2 建站复制路径创建（copyFromId=1、有序 copyData），
         * T2 类路径缺席时 fail-loud（D11）。列表为空等价于一条全默认首条（站点 1 不改名
         * 不改网络，主题 bundled/当前主题，种子 all）。
         */
        private List<SiteEntry> sites = new ArrayList<>();
        /**
         * 追加用户清单（P5 §8）。core 能力，各版本可用；幂等键为用户名（同名用户已在即
         * 跳过，续装不覆盖）。{@code readOnlyDemo: true} 的条目以固定 ID
         * {@code Constants.DEMO_USER_ID}（10）创建——演示站只读账号（C7）：
         * {@code ujcms.demo=true} 时 {@code BackendInterceptor} 对该 ID 强制仅 GET。
         */
        private List<UserEntry> users = new ArrayList<>();
        /**
         * 演示层（P5 §8 / D9）：驱动真实 Flowable 引擎——工作流永不进入种子数据。
         * 需商业 T1 类路径；社区版跳过并落报告行，安装照常完成。
         */
        private Demo demo = new Demo();
        /**
         * 会员注册开关（“全局设置 › 注册设置”）。缺省即保留引导值（关闭）。第三方登录的新用户需先注册用户名，
         * 清单开启第三方登录时应一并开启，否则首次登录的访客会停在 404。
         */
        private Register register = new Register();
        /**
         * 第三方登录凭据（“全局设置 › 第三方登录”，{@code Config.Oauth}）：给出应用ID与应用密钥的服务商即写入并启用，
         * 未给出的服务商保持原样。回调地址按默认站点地址推导（{@code {站点地址}/oauth/code/{服务商}}），无需配置。
         * 需商业 T2 类路径；社区版、基础版跳过并落报告行，安装照常完成。
         */
        private Oauth oauth = new Oauth();
        /**
         * AI 服务商（“全局设置 › AI服务商”，{@code Config.Ai}）：给出接口地址与模型即写入一个服务商、设为默认服务商
         * 并打开全局开关——各站点的 AI 功能开关默认开启，装完即可用。另可给出百度翻译的凭证（{@code mt.baidu}），
         * 写入机器翻译引擎配置并设为翻译服务商。更多服务商、其余服务商分工与额度在控制台维护。
         * 需商业 T2 类路径；社区版、基础版跳过并落报告行，安装照常完成。
         */
        private Ai ai = new Ai();

        /**
         * 首条站点清单；列表为空时返回全默认条目（零配置清单的站点 1 语义）。
         */
        public SiteEntry firstSite() {
            return sites.isEmpty() ? new SiteEntry() : sites.get(0);
        }

        public boolean isAuto() {
            return auto;
        }

        public void setAuto(boolean auto) {
            this.auto = auto;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        @Nullable
        public String getLibraryDir() {
            return libraryDir;
        }

        public void setLibraryDir(@Nullable String libraryDir) {
            this.libraryDir = libraryDir;
        }

        public Admin getAdmin() {
            return admin;
        }

        public void setAdmin(Admin admin) {
            this.admin = admin;
        }

        public List<SiteEntry> getSites() {
            return sites;
        }

        public void setSites(List<SiteEntry> sites) {
            this.sites = sites;
        }

        public List<UserEntry> getUsers() {
            return users;
        }

        public void setUsers(List<UserEntry> users) {
            this.users = users;
        }

        public Demo getDemo() {
            return demo;
        }

        public void setDemo(Demo demo) {
            this.demo = demo;
        }

        public Register getRegister() {
            return register;
        }

        public void setRegister(Register register) {
            this.register = register;
        }

        public Oauth getOauth() {
            return oauth;
        }

        public void setOauth(Oauth oauth) {
            this.oauth = oauth;
        }

        public Ai getAi() {
            return ai;
        }

        public void setAi(Ai ai) {
            this.ai = ai;
        }

        /**
         * 管理员凭据。用户名/密码缺省即不改动引导行。
         */
        public static class Admin {
            @Nullable
            private String username;
            @Nullable
            private String password;
            /**
             * 是否要求首次登录强制改密。安装设置凭据默认清除该标记（修复 docker
             * 旧 {@code init-password} 遗留 {@code pwd_must_change_='1'} 的缺口，C6）。
             */
            private boolean mustChangePassword = false;

            @Nullable
            public String getUsername() {
                return username;
            }

            public void setUsername(@Nullable String username) {
                this.username = username;
            }

            @Nullable
            public String getPassword() {
                return password;
            }

            public void setPassword(@Nullable String password) {
                this.password = password;
            }

            public boolean isMustChangePassword() {
                return mustChangePassword;
            }

            public void setMustChangePassword(boolean mustChangePassword) {
                this.mustChangePassword = mustChangePassword;
            }
        }

        /**
         * 站点清单条目。首条为站点 1：名称/域名/语言/子目录缺省即不改动，提供域名时端口缺省
         * 表示清除（80/443），{@code langOf} 不适用（站点 1 是引导主站点，fail-loud）。
         * 此后条目必须有名称（幂等键：同名站点已在即视为已建，续装收敛 lang/langOf/subDir，
         * 见 D45）；端口为全局配置项，仅首条生效。
         */
        public static class SiteEntry {
            @Nullable
            private String name;
            @Nullable
            private String protocol;
            @Nullable
            private String domain;
            @Nullable
            private Integer port;
            /**
             * 子目录寻址（multilingual addressing-core）。语言变体的推荐拓扑是同域名子目录
             * （{@code example.com/en/}），单域名模式下同域名的第二个清单站点<b>只能</b>靠
             * 子目录区分；普通独立条目同样可用。格式复用站点子目录约束
             * （{@code Site.SUB_DIR_PATTERN}，保留字 fail-loud）。缺省：变体继承主站点域名、
             * 子目录为空（仅多域名拓扑可行）。
             */
            @Nullable
            private String subDir;
            /**
             * 站点语言（BCP-47，如 {@code zh-CN}、{@code en}；格式复用
             * {@code Site.LANG_PATTERN}）。首条为站点 1 语言（缺省保留引导值 zh-CN）；
             * 此后条目须与 {@code langOf} 成对出现（语言变体两者缺一 fail-loud——
             * 独立站点的语言属安装后控制台配置面）。
             */
            @Nullable
            private String lang;
            /**
             * 多语言变体链接（Q3 → D44）：<b>站点名</b>引用（与幂等键同一约定），指向
             * 站点 1 或清单中<b>更早</b>的条目；按清单顺序处理，前向引用 fail-loud。
             * 解析归一到语言组主站点（引用变体时挂其主站点——组仅一层，杜绝链）；
             * 组完整性经 {@code SiteService.validateLangGroup} 校验（重复语言 fail-loud）。
             */
            @Nullable
            private String langOf;
            /**
             * 站点主题（来源 + 引用 + 种子范围）。
             */
            private Theme theme = new Theme();

            @Nullable
            public String getName() {
                return name;
            }

            public void setName(@Nullable String name) {
                this.name = name;
            }

            @Nullable
            public String getProtocol() {
                return protocol;
            }

            public void setProtocol(@Nullable String protocol) {
                this.protocol = protocol;
            }

            @Nullable
            public String getDomain() {
                return domain;
            }

            public void setDomain(@Nullable String domain) {
                this.domain = domain;
            }

            @Nullable
            public Integer getPort() {
                return port;
            }

            public void setPort(@Nullable Integer port) {
                this.port = port;
            }

            @Nullable
            public String getSubDir() {
                return subDir;
            }

            public void setSubDir(@Nullable String subDir) {
                this.subDir = subDir;
            }

            @Nullable
            public String getLang() {
                return lang;
            }

            public void setLang(@Nullable String lang) {
                this.lang = lang;
            }

            @Nullable
            public String getLangOf() {
                return langOf;
            }

            public void setLangOf(@Nullable String langOf) {
                this.langOf = langOf;
            }

            public Theme getTheme() {
                return theme;
            }

            public void setTheme(Theme theme) {
                this.theme = theme;
            }
        }

        /**
         * 站点主题清单（§7）：三种来源都经主题包库（§6）解析。
         * <ul>
         *   <li>{@code bundled}——类路径内置包；{@code ref} 缺省取站点当前主题名（站点 1 即
         *       {@code default}）。开发期 webapp 预展开授权目录已在时不覆盖、只种子。</li>
         *   <li>{@code library}——包库存量（含 {@code libraryDir} 摄取件）；{@code ref} 为
         *       {@code 名称} 或 {@code 名称@版本}，缺失即 fail-loud。</li>
         *   <li>{@code marketplace}——应用市场 {@code slug@版本}（版本可缺省取最新发布版），
         *       sha256 校验后写穿包库；库命中零重传。</li>
         * </ul>
         */
        public static class Theme {
            public static final String SOURCE_BUNDLED = "bundled";
            public static final String SOURCE_LIBRARY = "library";
            public static final String SOURCE_MARKETPLACE = "marketplace";

            private String source = SOURCE_BUNDLED;
            @Nullable
            private String ref;
            /**
             * 种子范围：{@code all} 导入主题全部演示种子（内容 + 构成）；{@code none} 跳过
             * 导入得到空站（Q5，替代旧 {@code ujcms.data-seed-enabled=false}）。
             */
            private String seed = "all";

            public String getSource() {
                return source;
            }

            public void setSource(String source) {
                this.source = source;
            }

            @Nullable
            public String getRef() {
                return ref;
            }

            public void setRef(@Nullable String ref) {
                this.ref = ref;
            }

            public String getSeed() {
                return seed;
            }

            public void setSeed(String seed) {
                this.seed = seed;
            }
        }

        /**
         * 追加用户条目（P5）。幂等键为用户名；缺省组织 1（总部）、用户组 8（管理组）、
         * 等级 0、普通用户、启用状态。{@code roles} 按<b>角色名</b>在站点 1 作用域解析
         * （引导角色即「管理角色」），名称不存在则该步 fail-loud。
         * {@code readOnlyDemo: true} 以固定 ID 10（{@code Constants.DEMO_USER_ID}）创建
         * ——配合 {@code ujcms.demo=true} 即演示站只读账号（C7）；该 ID 已被他名占用时
         * fail-loud。
         */
        public static class UserEntry {
            @Nullable
            private String username;
            @Nullable
            private String password;
            /**
             * 是否演示站只读账号：以固定 ID {@code Constants.DEMO_USER_ID}（10）创建
             */
            private boolean readOnlyDemo = false;
            /**
             * 角色名列表（站点 1 作用域解析）
             */
            private List<String> roles = new ArrayList<>();
            /**
             * 用户等级，缺省 0。须不大于所授各角色的等级（控制台约束同款）
             */
            @Nullable
            private Short rank;
            /**
             * 所属组织 ID，缺省 1（引导组织「总部」）
             */
            @Nullable
            private Long orgId;
            /**
             * 所属用户组 ID，缺省 8（引导用户组「管理组」）
             */
            @Nullable
            private Long groupId;

            @Nullable
            public String getUsername() {
                return username;
            }

            public void setUsername(@Nullable String username) {
                this.username = username;
            }

            @Nullable
            public String getPassword() {
                return password;
            }

            public void setPassword(@Nullable String password) {
                this.password = password;
            }

            public boolean isReadOnlyDemo() {
                return readOnlyDemo;
            }

            public void setReadOnlyDemo(boolean readOnlyDemo) {
                this.readOnlyDemo = readOnlyDemo;
            }

            public List<String> getRoles() {
                return roles;
            }

            public void setRoles(List<String> roles) {
                this.roles = roles;
            }

            @Nullable
            public Short getRank() {
                return rank;
            }

            public void setRank(@Nullable Short rank) {
                this.rank = rank;
            }

            @Nullable
            public Long getOrgId() {
                return orgId;
            }

            public void setOrgId(@Nullable Long orgId) {
                this.orgId = orgId;
            }

            @Nullable
            public Long getGroupId() {
                return groupId;
            }

            public void setGroupId(@Nullable Long groupId) {
                this.groupId = groupId;
            }
        }

        /**
         * 演示层清单（P5 §8 / D9）：经真实 Flowable 引擎供给演示工作流——部署两级审核
         * 流程定义并绑定 {@code processKey}，再以真实服务/真实用户制造在途审核状态。
         * 商业 T1 类路径缺席时整层跳过（报告行），安装照常完成。
         */
        public static class Demo {
            /**
             * 部署预制两级审核流程定义（sys_article / sys_form 两类）到站点 1 租户，并绑定
             * 演示栏目「新闻 › 国内」与演示表单类型的 {@code processKey}。按 key+租户幂等。
             */
            private boolean workflow = false;
            /**
             * 审核状态演示数据（需 {@code workflow: true}）：演示作者提交待审核文章
             * （产生真实 Flowable 任务）、一篇退回（含退回原因）、一篇两级流程中段
             * （审核中）。按标题幂等，重启不重复制造。
             */
            private boolean reviewStates = false;

            public boolean isWorkflow() {
                return workflow;
            }

            public void setWorkflow(boolean workflow) {
                this.workflow = workflow;
            }

            public boolean isReviewStates() {
                return reviewStates;
            }

            public void setReviewStates(boolean reviewStates) {
                this.reviewStates = reviewStates;
            }
        }

        /**
         * 会员注册清单：只含开关。{@code enabled} 缺省（{@code null}）即不改动“注册设置”。
         */
        public static class Register {
            @Nullable
            private Boolean enabled;

            @Nullable
            public Boolean getEnabled() {
                return enabled;
            }

            public void setEnabled(@Nullable Boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * 第三方登录清单：三个服务商各一组应用凭据（代码同 {@code Config.Oauth.PROVIDERS}）。
         */
        public static class Oauth {
            private OauthClient weixin = new OauthClient();
            private OauthClient qq = new OauthClient();
            private OauthClient weibo = new OauthClient();

            public OauthClient getWeixin() {
                return weixin;
            }

            public void setWeixin(OauthClient weixin) {
                this.weixin = weixin;
            }

            public OauthClient getQq() {
                return qq;
            }

            public void setQq(OauthClient qq) {
                this.qq = qq;
            }

            public OauthClient getWeibo() {
                return weibo;
            }

            public void setWeibo(OauthClient weibo) {
                this.weibo = weibo;
            }
        }

        /**
         * 一个服务商的应用凭据。应用ID与应用密钥须同时给出（只给其一 fail-loud）；都留空即不配置该服务商。
         */
        public static class OauthClient {
            /**
             * 应用ID（微信 AppID、QQ APP ID、微博 App Key）
             */
            private String clientId = "";
            /**
             * 应用密钥（微信 AppSecret、QQ APP Key、微博 App Secret）。建议经环境变量或 docker secret 注入
             */
            private String clientSecret = "";

            public String getClientId() {
                return clientId;
            }

            public void setClientId(String clientId) {
                this.clientId = clientId;
            }

            public String getClientSecret() {
                return clientSecret;
            }

            public void setClientSecret(String clientSecret) {
                this.clientSecret = clientSecret;
            }
        }

        /**
         * AI 清单：一个大模型服务商（安装后即默认服务商）与机器翻译引擎凭证（安装后即翻译服务商），二者各自可缺省。
         */
        public static class Ai {
            private AiProvider provider = new AiProvider();
            private AiMt mt = new AiMt();

            public AiProvider getProvider() {
                return provider;
            }

            public void setProvider(AiProvider provider) {
                this.provider = provider;
            }

            public AiMt getMt() {
                return mt;
            }

            public void setMt(AiMt mt) {
                this.mt = mt;
            }
        }

        /**
         * 机器翻译引擎清单（{@code Config.Ai.Mt}）。目前只收百度翻译；有道翻译、DeepL 在控制台配置。
         */
        public static class AiMt {
            private AiMtBaidu baidu = new AiMtBaidu();

            public AiMtBaidu getBaidu() {
                return baidu;
            }

            public void setBaidu(AiMtBaidu baidu) {
                this.baidu = baidu;
            }
        }

        /**
         * 百度翻译开放平台的凭证。APP ID 与密钥须同时给出（只给其一 fail-loud）；都留空即不配置。
         */
        public static class AiMtBaidu {
            /**
             * 百度翻译开放平台 APP ID
             */
            private String appId = "";
            /**
             * 百度翻译密钥。建议经环境变量或 docker secret 注入
             */
            private String secret = "";

            /**
             * 清单是否给出了百度翻译凭证（任一字段非空）
             */
            public boolean declared() {
                return !StringUtils.isAllBlank(appId, secret);
            }

            public String getAppId() {
                return appId;
            }

            public void setAppId(String appId) {
                this.appId = appId;
            }

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }
        }

        /**
         * 一个 OpenAI 兼容协议的大模型服务商（字段同 {@code Config.Ai.Provider}）。接口地址与模型须同时给出
         * （只给其一 fail-loud）；全部留空即不配置。
         */
        public static class AiProvider {
            /**
             * 名称（运营者可见，AI 日志按它记录）。缺省取模型名
             */
            private String name = "";
            /**
             * 接口地址，不含 {@code /chat/completions}，如 https://api.deepseek.com/v1
             */
            private String baseUrl = "";
            /**
             * API密钥。建议经环境变量或 docker secret 注入；Ollama / vLLM 等内网部署可留空
             */
            private String apiKey = "";
            /**
             * 模型
             */
            private String model = "";
            /**
             * 请求附加参数（JSON 对象文本，原样合并进每次请求体）。推理模型用它关闭深度思考，
             * 如智谱 / 火山方舟 {@code {"thinking":{"type":"disabled"}}}、阿里云百炼 {@code {"enable_thinking":false}}
             */
            private String extraParams = "";

            /**
             * 清单是否给出了服务商（任一字段非空）
             */
            public boolean declared() {
                return !StringUtils.isAllBlank(name, baseUrl, apiKey, model, extraParams);
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getModel() {
                return model;
            }

            public void setModel(String model) {
                this.model = model;
            }

            public String getExtraParams() {
                return extraParams;
            }

            public void setExtraParams(String extraParams) {
                this.extraParams = extraParams;
            }
        }
    }
}
