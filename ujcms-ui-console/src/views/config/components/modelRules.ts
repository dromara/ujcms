import { currentUser } from '@/stores/useCurrentUser';

// 模型的客户端规则（docs/spec/model-management/plan.md），与服务端同规：
// M5 全局模型无全局数据权限时只读；M6 全局设置模型（ID 1）、用户模型（ID 2）为系统行，类型为单例。

export interface ModelUsage {
  modelId: string;
  count: number;
  external: number;
}

/** 单例模型类型：每种只有一个系统行，各处按固定 ID 取用，不提供新增与排序 */
export const SINGLETON_MODEL_TYPES: readonly string[] = ['global', 'user'];
/** 系统模型 ID（服务端 Model.SYSTEM_MODEL_IDS） */
export const SYSTEM_MODEL_IDS: readonly string[] = ['1', '2'];

export const isSingletonType = (type: string | undefined): boolean => type != null && SINGLETON_MODEL_TYPES.includes(type);
export const isSystemModel = (bean: any): boolean => bean != null && SYSTEM_MODEL_IDS.includes(String(bean.id));
/** 全局模型属全局数据：无全局数据权限时只可查看（服务端同规），不给出必然 403 的操作 */
export const isReadonlyModel = (bean: any): boolean => bean?.scope === 2 && !currentUser.globalPermission;
