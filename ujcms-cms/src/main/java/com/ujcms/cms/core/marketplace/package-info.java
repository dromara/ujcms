/**
 * 远程应用市场客户端（remote-marketplace C1，kickoff §7）。
 *
 * <p>CMS 后端是商店的唯一客户端（D-K3）：本包封装对 {@code marketplace.ujcms.com} 实例 API
 * （{@code contracts/marketplace-api.yaml}，D-K8）的目录读取与下载授权调用；控制台永不跨源直连。
 * 安装本身复用既有 {@link com.ujcms.cms.core.service.ThemeInstallService} 管线，本包只做
 * 取包 + 完整性校验（sha256，D-K7）这一步。
 */
@NullMarked
package com.ujcms.cms.core.marketplace;

import org.jspecify.annotations.NullMarked;
