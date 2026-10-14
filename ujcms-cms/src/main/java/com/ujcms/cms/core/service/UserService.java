package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.ContentStatEvictor;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.domain.generated.GeneratedUser;
import com.ujcms.cms.core.listener.GroupDeleteListener;
import com.ujcms.cms.core.listener.OrgDeleteListener;
import com.ujcms.cms.core.listener.UserDeleteListener;
import com.ujcms.cms.core.mapper.*;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PiiBlindIndex;
import com.ujcms.cms.core.support.StaticProps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ujcms.cms.core.domain.User.ANONYMOUS_ID;

/**
 * 用户 Service
 *
 * @author PONY
 */
@Service
public class UserService implements OrgDeleteListener, GroupDeleteListener {
    private final PasswordEncoder passwordEncoder;
    private final AttachmentService attachmentService;
    private final ModelService modelService;
    private final HtmlSanitizeService sanitizeService;
    private final UserMapper mapper;
    private final UserExtMapper extMapper;
    private final UserOpenidMapper openidMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserOrgMapper userOrgMapper;
    private final OrgMapper orgMapper;
    private final SnowflakeSequence snowflakeSequence;
    private final ContentStatEvictor contentStatEvictor;

    public UserService(PasswordEncoder passwordEncoder, AttachmentService attachmentService,
                       ModelService modelService, HtmlSanitizeService sanitizeService,
                       UserMapper mapper, UserExtMapper extMapper, UserOpenidMapper openidMapper,
                       UserRoleMapper userRoleMapper, UserOrgMapper userOrgMapper,
                       OrgMapper orgMapper, SnowflakeSequence snowflakeSequence,
                       ContentStatEvictor contentStatEvictor) {
        this.passwordEncoder = passwordEncoder;
        this.attachmentService = attachmentService;
        this.modelService = modelService;
        this.sanitizeService = sanitizeService;
        this.mapper = mapper;
        this.extMapper = extMapper;
        this.openidMapper = openidMapper;
        this.userRoleMapper = userRoleMapper;
        this.userOrgMapper = userOrgMapper;
        this.orgMapper = orgMapper;
        this.snowflakeSequence = snowflakeSequence;
        this.contentStatEvictor = contentStatEvictor;
    }

    /**
     * 在加载 / 保存 User 前注入用户模型。{@link ModelService#selectUserModel()} 返回 ID=2
     * 的种子用户模型；如不存在则抛异常（说明数据库未初始化）。
     */
    private void attachUserModel(User bean) {
        if (bean.getUserModel() == null) {
            bean.setUserModel(modelService.selectUserModel());
        }
    }

    private void insertUserOrg(Long userId, List<Long> orgIds) {
        for (Long orgId : orgIds) {
            Org org = orgMapper.select(orgId);
            if (org == null || userOrgMapper.select(userId, orgId) != null) {
                continue;
            }
            userOrgMapper.insert(new UserOrg(userId, orgId, org.getOrder()));
        }
    }

    /**
     * 由手机号 / 邮箱计算并写入盲索引列。所有写库路径（insert / update / updatePassword）统一调用，
     * 保证索引始终与值同步；空值对应索引为 {@code null}（UNIQUE 下允许多行无值并存）。
     * 集中于此后，{@code selectByMobile / selectByEmail} 的众多调用方无需改动。
     *
     * @see PiiBlindIndex
     */
    private void applyPiiIndex(User bean) {
        bean.setMobileIndex(PiiBlindIndex.mobileIndex(bean.getMobile()));
        bean.setEmailIndex(PiiBlindIndex.emailIndex(bean.getEmail()));
    }

    /**
     * 查询键里指向手机号 / 邮箱列的那一段：操作符、可选的分组、列名、可选的类型
     */
    private static final Pattern PII_QUERY_KEY = Pattern.compile("^([A-Za-z]+)_((?:\\d+(?:-\\d+)?_)?)(mobile|email)(?:_[A-Za-z]+)?$");
    private static final Pattern PII_ORDER_BY = Pattern.compile("^(?:\\d+(?:-\\d+)?_)?(mobile|email)(?:_(?:asc|desc))?$");

    /**
     * 把列表查询里针对手机号 / 邮箱的条件改写到盲索引上（docs/spec/article-data-permission/plan.md §13.5）：两列整列加密、
     * 随机 IV，按密文筛选永远筛不到，按密文排序只是乱序。值按精确匹配（规范化后取盲索引）——加密列只能精确查找；
     * 操作符不论是什么都按精确处理。空值即“没有填”；按这两列排序一律拒绝
     */
    public static void rewritePiiQuery(Map<String, Object> queryMap) {
        Map<String, Object> rewritten = new HashMap<>();
        for (Iterator<Map.Entry<String, Object>> it = queryMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            if (QueryParser.ORDER_BY.equals(key) && entry.getValue() instanceof String orderBy) {
                for (String item : StringUtils.split(orderBy, ',')) {
                    if (PII_ORDER_BY.matcher(item.trim()).matches()) {
                        throw new Http400Exception("Query not allowed: " + item.trim());
                    }
                }
                continue;
            }
            Matcher matcher = PII_QUERY_KEY.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            it.remove();
            String group = matcher.group(2);
            String column = matcher.group(3);
            String value = Objects.toString(entry.getValue(), "");
            String index = "mobile".equals(column) ? PiiBlindIndex.mobileIndex(value) : PiiBlindIndex.emailIndex(value);
            if (index == null) {
                rewritten.put("IsNull_" + group + column + "Index", "true");
            } else {
                rewritten.put("EQ_" + group + column + "Index", index);
            }
        }
        queryMap.putAll(rewritten);
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(User bean, UserExt ext, List<Long> orgIds) {
        bean.setId(snowflakeSequence.nextId());
        ext.setId(bean.getId());
        attachUserModel(bean);
        bean.disassembleCustoms(bean.getUserModel(), sanitizeService);
        applyPiiIndex(bean);
        mapper.insert(bean);
        extMapper.insert(ext);
        insertUserOrg(bean.getId(), orgIds);
        attachmentService.insertRefer(GeneratedUser.TABLE_NAME, bean.getId(), bean.getAvatarList());
        contentStatEvictor.user();
    }

    @Transactional(rollbackFor = Exception.class)
    public void insertOpenid(UserOpenid bean) {
        openidMapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteOpenid(Long userId, String provider) {
        return openidMapper.delete(userId, provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext, @Nullable Long ancestorOrgId, List<Long> orgIds) {
        update(bean);
        update(ext);
        userOrgMapper.deleteByUserIdAndAncestorOrgId(bean.getId(), ancestorOrgId);
        insertUserOrg(bean.getId(), orgIds);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, UserExt ext) {
        update(bean);
        update(ext);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean, List<Long> roleIds) {
        update(bean);
        userRoleMapper.deleteByUserId(bean.getId());
        roleIds.forEach(roleId -> userRoleMapper.insert(new UserRole(bean.getId(), roleId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(User bean) {
        attachUserModel(bean);
        bean.disassembleCustoms(bean.getUserModel(), sanitizeService);
        applyPiiIndex(bean);
        attachmentService.updateRefer(GeneratedUser.TABLE_NAME, bean.getId(), bean.getAvatarList());
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserExt ext) {
        extMapper.update(ext);
    }

    /**
     * 保存当前用户的控制台偏好（docs/spec/console-shell）。只写 preferences_ 一列，不经 {@link #update(UserExt)} 的全列 SET。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePreferences(Long userId, UserExt.Preferences preferences) {
        UserExt carrier = new UserExt();
        carrier.setPreferences(preferences);
        extMapper.updatePreferences(userId, carrier.getPreferencesJson());
    }

    /**
     * 修改口令。用于用户自助改密 / 短信邮箱验证码自助重置——成功后清除“首次登录强制改密”标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(User user, UserExt userExt, String password) {
        updatePassword(user, userExt, password, false);
    }

    /**
     * 修改口令并设置“首次登录强制改密”标记。后台管理员重置他人口令时传 {@code mustChangeNext=true}，
     * 要求该用户下次登录先改密；用户自助改密 / 验证码自助重置传 {@code false} 清除该标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(User user, UserExt userExt, String password, boolean mustChangeNext) {
        String origPassword = user.getPassword();
        if (StringUtils.isNotBlank(origPassword)) {
            user.addHistoryPassword(origPassword);
        }
        user.setPassword(passwordEncoder.encode(password));
        user.setPasswordModified(OffsetDateTime.now());
        user.setPwdMustChange(mustChangeNext);
        userExt.setErrorCount(0);
        applyPiiIndex(user);
        mapper.update(user);
        extMapper.update(userExt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void loginSuccess(UserExt userExt, String ip) {
        userExt.setLoginCount(userExt.getLoginCount() + 1);
        userExt.setLoginDate(OffsetDateTime.now());
        userExt.setLoginIp(ip);
        userExt.setErrorCount(0);
        extMapper.update(userExt);
    }


    @Transactional(rollbackFor = Exception.class)
    public void loginFailure(UserExt userExt, int lockMinutes) {
        OffsetDateTime now = OffsetDateTime.now();
        if (Duration.between(userExt.getErrorDate(), now).toMinutes() > lockMinutes) {
            // 超过锁定时间，重新计数
            userExt.setErrorCount(1);
        } else {
            // 累加
            userExt.setErrorCount(userExt.getErrorCount() + 1);
        }
        userExt.setErrorDate(now);
        extMapper.update(userExt);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        deleteListeners.forEach(it -> it.preUserDelete(id));
        userRoleMapper.deleteByUserId(id);
        attachmentService.deleteRefer(GeneratedUser.TABLE_NAME, id);
        openidMapper.deleteByUserId(id);
        extMapper.delete(id);
        int count = mapper.delete(id);
        contentStatEvictor.user();
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public User select(Long id) {
        User user = mapper.select(id);
        if (user != null) {
            attachUserModel(user);
        }
        return user;
    }

    @Nullable
    public User selectByUsername(String username) {
        return mapper.selectByUsername(username);
    }

    @Nullable
    public User selectByEmail(String email) {
        // 邮箱以盲索引精确匹配：对查询值做与写入完全一致的规范化 + HMAC-SM3 后比对 email_index_。
        String emailIndex = PiiBlindIndex.emailIndex(email);
        return emailIndex == null ? null : mapper.selectByEmail(emailIndex);
    }

    @Nullable
    public User selectByMobile(String mobile) {
        String mobileIndex = PiiBlindIndex.mobileIndex(mobile);
        return mobileIndex == null ? null : mapper.selectByMobile(mobileIndex);
    }

    /**
     * 盲索引回填（单批，独立事务）。取出一批仍缺索引的用户（手机号 / 邮箱非空但索引列为 {@code NULL}），
     * 逐行计算并写回索引列，同时把历史明文转为 SM4-GCM 密文。
     * <ul>
     *   <li><strong>批量 / 可续跑</strong>：键集分页（{@code id_ > lastId}）+ PageHelper 限批；
     *       每批独立提交，中断后从已提交进度继续。控制器循环调用，直至返回 {@code processed == 0}。</li>
     *   <li><strong>幂等</strong>：已写入索引的行不再被选中；重复运行只处理残留行。</li>
     *   <li><strong>fail-loud</strong>：写入前检测盲索引冲突（同值不同用户）即抛异常中止，不静默丢数据；
     *       密文无法解密的行由 {@link com.ujcms.cms.core.security.Sm4StringTypeHandler} 在读取时抛异常中止。</li>
     * </ul>
     * 不在应用启动时自动运行——仅由后台维护端点显式触发。
     *
     * @param lastId    上一批已处理的最大用户ID（首批传 0）
     * @param batchSize 批大小
     * @return 本批处理结果（已处理条数 + 本批最大用户ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public PiiBackfillResult backfillPiiBatch(long lastId, int batchSize) {
        List<User> batch = PageMethod.offsetPage(0, batchSize, false)
                .doSelectPage(() -> mapper.listForPiiBackfill(lastId));
        long maxId = lastId;
        int processed = 0;
        for (User user : batch) {
            maxId = Math.max(maxId, user.getId());
            String mobileIndex = PiiBlindIndex.mobileIndex(user.getMobile());
            String emailIndex = PiiBlindIndex.emailIndex(user.getEmail());
            if (mobileIndex != null && mapper.existsByMobileIndex(mobileIndex, user.getId()) > 0) {
                throw new IllegalStateException("手机号盲索引冲突：用户 ID=" + user.getId()
                        + " 与已有用户的手机号重复，请先消除重复后再回填。");
            }
            if (emailIndex != null && mapper.existsByEmailIndex(emailIndex, user.getId()) > 0) {
                throw new IllegalStateException("邮箱盲索引冲突：用户 ID=" + user.getId()
                        + " 与已有用户的邮箱重复，请先消除重复后再回填。");
            }
            mapper.updatePiiIndex(user.getId(), user.getMobile(), mobileIndex, user.getEmail(), emailIndex);
            processed++;
        }
        return new PiiBackfillResult(processed, maxId);
    }

    /**
     * 盲索引回填单批结果。
     *
     * @param processed 本批实际处理（写入索引）的用户数；为 0 表示已无残留、回填完成
     * @param lastId    本批已处理的最大用户ID，作为下一批的游标
     */
    public record PiiBackfillResult(int processed, long lastId) {
    }

    @Nullable
    public User selectByOpenid(String provider, String openid) {
        return mapper.selectByOpenid(provider, openid);
    }

    public List<User> selectList(UserArgs args) {
        Map<String, Object> queryMap = args.getQueryMap();
        if (queryMap != null) {
            queryMap = new HashMap<>(queryMap);
            rewritePiiQuery(queryMap);
        }
        QueryInfo queryInfo = QueryParser.parse(queryMap, GeneratedUser.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo, args.getOrgId());
    }

    public User anonymous() {
        return Optional.ofNullable(select(ANONYMOUS_ID)).orElseThrow(() ->
                new IllegalStateException("Anonymous user(ID=0) not found."));
    }

    public List<User> selectList(UserArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<User> selectPage(UserArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }

    /**
     * 统计用户数量
     *
     * @param created 创建日期
     * @return 用户数量
     */
    public int countByCreated(@Nullable OffsetDateTime created) {
        return mapper.countByCreated(created);
    }

    /**
     * 统计未登记手机号码的后台用户数量。开启双因子认证后，这些用户将无法登录
     */
    public long countBackendUsersWithoutMobile() {
        // 开启三权分立时超级管理员（id=1）被强制停用，本就不能登录，不计入
        return mapper.countBackendUsersWithoutMobile(!StaticProps.isSeparationOfDuties());
    }

    public boolean existsByOrgId(Long orgId) {
        return mapper.existsByOrgId(orgId) > 0;
    }

    public boolean existsByGroupId(Long groupId) {
        return mapper.existsByGroupId(groupId) > 0;
    }

    public boolean existsByRoleId(Long roleId, Long notOrgId) {
        return mapper.existsByRoleId(roleId, notOrgId) > 0;
    }

    @Override
    public void preOrgDelete(Long orgId) {
        if (existsByOrgId(orgId)) {
            throw new LogicException("error.refer.user");
        }
    }

    @Override
    public void preGroupDelete(Long groupId) {
        if (existsByGroupId(groupId)) {
            throw new LogicException("error.refer.user");
        }
    }

    private List<UserDeleteListener> deleteListeners = Collections.emptyList();

    @Lazy
    @Autowired(required = false)
    public void setDeleteListeners(List<UserDeleteListener> deleteListeners) {
        this.deleteListeners = deleteListeners;
    }
}