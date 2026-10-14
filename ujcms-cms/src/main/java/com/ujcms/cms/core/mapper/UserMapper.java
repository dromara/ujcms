package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.User;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface UserMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(User bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(User bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Org selectRefer(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    User select(Long id);

    /**
     * 根据用户名获取用户
     *
     * @param username 用户名
     * @return 用户对象
     */
    @Nullable
    User selectByUsername(String username);

    /**
     * 根据邮箱盲索引获取用户。索引由 {@code UserService.selectByEmail} 对规范化邮箱计算后传入。
     *
     * @param emailIndex 邮箱盲索引（HMAC-SM3）
     * @return 用户对象。没有找到数据，则返回 {@code null}
     * @see com.ujcms.cms.core.security.PiiBlindIndex#emailIndex(String)
     */
    @Nullable
    User selectByEmail(@Param("emailIndex") String emailIndex);

    /**
     * 根据手机号盲索引获取用户。索引由 {@code UserService.selectByMobile} 对规范化手机号计算后传入。
     *
     * @param mobileIndex 手机号盲索引（HMAC-SM3）
     * @return 用户对象。没有找到数据，则返回 {@code null}
     * @see com.ujcms.cms.core.security.PiiBlindIndex#mobileIndex(String)
     */
    @Nullable
    User selectByMobile(@Param("mobileIndex") String mobileIndex);

    /**
     * 是否存在使用该邮箱盲索引的其它用户（排除自身）。盲索引回填时用于在写入前发现冲突并明确报错。
     *
     * @param emailIndex 邮箱盲索引
     * @param excludeId  需排除的用户ID（通常为正在处理的用户自身）
     * @return 存在返回大于 0
     */
    int existsByEmailIndex(@Param("emailIndex") String emailIndex, @Param("excludeId") Long excludeId);

    /**
     * 是否存在使用该手机号盲索引的其它用户（排除自身）。
     *
     * @param mobileIndex 手机号盲索引
     * @param excludeId   需排除的用户ID
     * @return 存在返回大于 0
     */
    int existsByMobileIndex(@Param("mobileIndex") String mobileIndex, @Param("excludeId") Long excludeId);

    /**
     * 盲索引回填：按 ID 升序、键集分页（{@code id_ > lastId}）取出仍需计算索引的用户
     * （手机号 / 邮箱非空但对应索引列为 {@code NULL}）。返回对象的手机号 / 邮箱已由 TypeHandler 解密为明文。
     * 行数限制由调用方用 PageHelper 施加（跨库方言安全），本方法不在 SQL 中写 LIMIT。
     *
     * @param lastId 上一批已处理的最大用户ID（首批传 0）
     * @return 待回填用户列表（仅含 id / 手机号 / 邮箱 / 两个索引列）
     */
    List<User> listForPiiBackfill(@Param("lastId") long lastId);

    /**
     * 盲索引回填：重写单个用户的手机号 / 邮箱密文（顺带把历史明文转为密文）并写入对应盲索引列。
     *
     * @param id          用户ID
     * @param mobile      手机号明文（写入时由 TypeHandler 加密）；可为 {@code null}
     * @param mobileIndex 手机号盲索引；值为空时为 {@code null}
     * @param email       邮箱明文（写入时由 TypeHandler 加密）；可为 {@code null}
     * @param emailIndex  邮箱盲索引；值为空时为 {@code null}
     * @return 更新条数
     */
    int updatePiiIndex(@Param("id") Long id,
                       @Param("mobile") @Nullable String mobile, @Param("mobileIndex") @Nullable String mobileIndex,
                       @Param("email") @Nullable String email, @Param("emailIndex") @Nullable String emailIndex);

    /**
     * 根据提供商和openid获取用户
     *
     * @param provider 提供商
     * @param openid   openid
     * @return 用户对象
     */
    @Nullable
    User selectByOpenid(@Param("provider") String provider, @Param("openid") String openid);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @param orgId     组织ID
     * @return 数据列表
     */
    List<User> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo, @Nullable @Param("orgId") Long orgId);

    /**
     * 统计用户数量
     *
     * @param created 创建日期
     * @return 用户数量
     */
    int countByCreated(@Param("created") @Nullable OffsetDateTime created);

    /**
     * 统计未登记手机号码的后台用户数量。后台用户指拥有任一角色的正常状态用户，以及超级管理员（id=1）
     *
     * @param includeRoot 是否计入超级管理员。开启三权分立时超级管理员被强制停用，不计入
     * @return 用户数量
     */
    long countBackendUsersWithoutMobile(@Param("includeRoot") boolean includeRoot);

    /**
     * 根据组织ID统计用户数量
     *
     * @param orgId 组织ID
     * @return 用户数量
     */
    int existsByOrgId(Long orgId);

    /**
     * 根据用户组ID统计用户数量
     *
     * @param groupId 用户组ID
     * @return 用户数量
     */
    int existsByGroupId(Long groupId);

    /**
     * 根据角色ID和组织ID统计数量
     *
     * @param roleId   角色ID
     * @param notOrgId 非本组织ID
     * @return 数据条数
     */
    int existsByRoleId(@Param("roleId") Long roleId, @Param("notOrgId") Long notOrgId);
}