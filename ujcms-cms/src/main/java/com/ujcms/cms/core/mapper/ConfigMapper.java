package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Config;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 全局配置 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface ConfigMapper {
    /**
     * 插入数据（仅系统核心引导使用：空库首装写入固定 ID 1 的唯一行）
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(Config bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(Config bean);

    /**
     * 更新端口
     *
     * @param port 端口。可以为 null
     * @return 更新条数
     */
    int updatePort(@Nullable @Param("port") Integer port);

    /**
     * 更新应用市场连接设置（remote-marketplace C2 / D-C1-1）。单列写入：令牌随刷新在运行期轮换，
     * 不进通用 {@code update}——整行保存不得用陈旧值复活已作废的 refresh token。
     *
     * @param marketplaceSettings 连接设置 JSON（SM4 加密落库）。{@code null} 即断开
     * @return 更新条数
     */
    int updateMarketplaceSettings(@Nullable @Param("marketplaceSettings") String marketplaceSettings);

    /**
     * 获取唯一数据。全局表只能且必须查找唯一一条 {@code ID} 为 {@code 1} 的数据。
     *
     * @return 唯一的 Config 对象
     */
    @Nullable
    Config findUnique();

    // ========== 安装状态机（install-flow §3 / D1 / D12） ==========
    // install_state_ / install_token_hash_ 只经以下专用单列语句读写：不进实体、不进
    // resultMap、不进通用 update/insert——令牌摘要绝不进入任何序列化路径，状态读取
    // 绕过二级缓存（跨节点翻转不能被本节点陈旧缓存遮蔽）。

    /**
     * 读取安装状态。{@code null} 表示无 config 行（NOT_INSTALLED）或行存在但状态未写
     * （视同 coreReady——数据脚本与状态写入之间的崩溃续装窗口）。
     *
     * @return 安装状态（coreReady/installed），可为 null
     */
    @Nullable
    String selectInstallState();

    /**
     * config 行数。区分 NOT_INSTALLED（无行）与状态列为 NULL 的 coreReady 行。
     *
     * @return 行数
     */
    int countConfig();

    /**
     * 读取一次性安装令牌摘要（SM3）。
     *
     * @return 令牌摘要，可为 null（未生成或已随安装完成作废）
     */
    @Nullable
    String selectInstallTokenHash();

    /**
     * 写入 coreReady 状态与一次性安装令牌摘要。守卫更新：绝不降级已安装系统；
     * 同时把状态为 NULL 的行归一为 coreReady。
     *
     * @param tokenHash 令牌 SM3 摘要
     * @return 更新条数（0 表示系统已安装）
     */
    int updateInstallToken(@Param("tokenHash") String tokenHash);

    /**
     * 安装完成翻转（守卫单行 UPDATE，D1）：置 'installed' 并清空令牌摘要。
     * 并发安装器/集群节点下原子——只有一个调用方能得到 1。
     *
     * @return 更新条数（1 = 本次翻转成功；0 = 已由他方完成）
     */
    int flipInstalled();
}