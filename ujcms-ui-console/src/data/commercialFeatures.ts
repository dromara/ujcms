import data from './commercialFeatures.json';

/**
 * 商业功能条目。commercialFeatures.json 由 scripts/generate-commercial-features.mjs
 * 从注册表 ujcms-cms/src/test/resources/commercial-features/features.yaml 生成
 * （勿手改；build/lint 以 --check 拦截漂移）。
 */
export interface CommercialFeature {
  id: string;
  /** 所需商业版级别：1=T1 基础版，2=T2 标准版，3=T3 高级版，4=T4 旗舰版 */
  tier: number;
  permissions: string[];
  /** 控制台入口路由（无独立页面的功能为空） */
  route?: string;
}

export const commercialFeatures: CommercialFeature[] = data.features;

const byPermission: Record<string, CommercialFeature> = {};
commercialFeatures.forEach((feature) => {
  feature.permissions.forEach((permission) => {
    byPermission[permission] = feature;
  });
});

export const findCommercialFeature = (permission?: string): CommercialFeature | undefined =>
  permission ? byPermission[permission] : undefined;
