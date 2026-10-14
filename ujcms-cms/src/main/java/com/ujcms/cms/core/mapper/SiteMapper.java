package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.common.db.tree.TreeEntityMapper;
import com.ujcms.common.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 站点 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteMapper extends TreeEntityMapper<Site> {
    /**
     * 对站点行加行锁（{@code select … for update}）：本站生成别名的写入由此依次进行
     * （docs/spec/article-data-permission/plan.md D22）。须在一个已开启的事务里调用
     *
     * @return 行存在时返回其ID，否则 {@code null}
     */
    @Nullable
    Long lockById(@Param("id") Long id);

    /**
     * 根据主键获取引用对象（不包括关联对象属性）
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Site selectRefer(Long id);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param isQueryHasChildren 是否查询包含子站点
     * @param fullOrgId          组织ID。属于该组织的上级或下级的站点都符合条件
     * @return 数据列表
     */
    List<Site> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                         @Param("isQueryHasChildren") boolean isQueryHasChildren,
                         @Nullable @Param("fullOrgId") Long fullOrgId);

    /**
     * 更新基本信息
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int updateBase(Site bean);

    /**
     * 更新域名
     *
     * @param id       站点ID
     * @param protocol 协议
     * @param domain   域名
     * @return 插入条数
     */
    int updateDomain(@Param("id") Long id, @Param("protocol") String protocol, @Param("domain") String domain);

    /**
     * 更新站点状态
     *
     * @param id     站点ID
     * @param status 站点状态
     * @return 更新条数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Short status);

    /**
     * 根据父站点ID获取子站点列表
     *
     * @param parentId 父站点ID
     * @return 子站点列表
     */
    List<Site> listChildren(Long parentId);

    /**
     * 根据 用户ID 和 组织ID 查找站点
     *
     * @param userId 用户ID
     * @param orgId  组织ID
     * @return 站点列表
     */
    List<Site> listByUserIdAndOrgId(@Param("userId") Long userId, @Param("orgId") Long orgId);

    /**
     * 获取所有站点。用于整理树形结构，只获取必须字段
     *
     * @return 数据列表
     */
    List<Site> listForTidy();

    /**
     * 根据组织ID查找站点ID
     *
     * @param orgId 组织ID
     * @return 站点ID列表
     */
    List<Long> listIdByOrgId(Long orgId);

    /**
     * 查询站点数量
     *
     * @return 站点数量
     */
    int count();

    /**
     * 是否有站点绑定该站点设置模型（模型删除守卫）
     *
     * @param modelId 模型ID
     * @return 1：存在；0：不存在
     */
    int existsByModelId(Long modelId);

    /**
     * 根据域名查找站点
     *
     * @param domain 域名
     * @return 站点
     */
    @Nullable
    Site findByDomain(String domain);

    /**
     * 根据 (域名, 子目录) 组合查找站点。组合寻址（multilingual P2）的权威匹配，
     * 使 a.example.com/en 与 b.example.com/en 可共存
     *
     * @param domain 域名
     * @param subDir 子目录
     * @return 站点。没有找到数据，则返回 {@code null}
     */
    @Nullable
    Site findByDomainAndSubDir(@Param("domain") String domain, @Param("subDir") String subDir);

    /**
     * 根据子目录查找站点列表（不区分域名，按ID排序）。
     * 取代原单行 findBySubDir：跨域名重复子目录出现后，全局查找可能多条，由服务层裁决歧义
     *
     * @param subDir 子目录
     * @return 站点列表
     */
    List<Site> listBySubDir(String subDir);

    /**
     * 子目录站点列表（正常状态，按 order_, id_ 排序）：根站点 robots.txt 为同一主机下的子目录站点补 Sitemap 行
     *
     * @param domain 域名。多域名模式下限定同域名；单域名模式传 {@code null}（所有站点共享一个主机）
     * @return 子目录站点列表
     */
    List<Site> listSubDirSites(@Param("domain") @Nullable String domain);

    /**
     * 统计 (域名, 子目录) 组合地址相同的站点数量。用于多域名模式下的地址唯一性校验
     *
     * @param domain    域名
     * @param subDir    子目录。为 {@code null} 时匹配 {@code sub_dir_ is null} 的行
     * @param excludeId 排除的站点ID（通常为待保存站点自身）。可为 {@code null}
     * @return 站点数量
     */
    int countByDomainAndSubDir(@Param("domain") String domain, @Param("subDir") @Nullable String subDir,
                               @Param("excludeId") @Nullable Long excludeId);

    /**
     * 统计子目录相同的站点数量（不区分域名）。用于单域名模式下的子目录全局唯一性校验
     *
     * @param subDir    子目录
     * @param excludeId 排除的站点ID（通常为待保存站点自身）。可为 {@code null}
     * @return 站点数量
     */
    int countBySubDir(@Param("subDir") String subDir, @Param("excludeId") @Nullable Long excludeId);

    /**
     * 统计语言组主站点下的语言变体站点数量。用于删除主站点前的完整性校验
     *
     * @param langParentId 语言组主站点ID
     * @return 变体站点数量
     */
    int countByLangParentId(@Param("langParentId") Long langParentId);

    /**
     * 统计语言组内（含主站点自身）指定语言的站点数量。用于同组语言唯一性校验
     *
     * @param masterId  语言组主站点ID
     * @param lang      语言(BCP-47)
     * @param excludeId 排除的站点ID（通常为待保存站点自身）。可为 {@code null}
     * @return 站点数量
     */
    int countByLangGroupAndLang(@Param("masterId") Long masterId, @Param("lang") String lang,
                                @Param("excludeId") @Nullable Long excludeId);

    /**
     * 获取语言组成员列表（含主站点自身；仅正常状态；按 order_, id_ 排序）
     *
     * @param masterId 语言组主站点ID
     * @return 语言组成员站点列表
     */
    List<Site> listByLangGroup(@Param("masterId") Long masterId);
}