import { currentUser } from '@/stores/useCurrentUser';

/**
 * 文章的状态操作规则。文章列表的行操作、批量操作的适用篇数、编辑器顶栏的按钮共用这一份
 * （docs/spec/article-management/plan.md Phase 3）。
 *
 * 判定与服务端 ArticleService 一致。服务端对不适用的文章是静默略过；控制台据此事先告诉操作员哪些会被略过，
 * 不给出点了没反应的按钮。
 *
 * 状态值：0 已发布，1 已归档，5 待发布，10 草稿，11 待审核，12 审核中，20 已删除，21 已下线，22 已退回
 */
/** 文章（列表行或表单里的 bean）。只读取 `status` 与 `processInstanceId`；表格插槽给出的行没有具体类型，所以不作更严的约束 */
export type ArticleStatusRow = Record<string, any>;

/** 状态页签的顺序，按文章的一生排列：写作与审核 → 发布 → 下架 → 回收站 */
export const articleStatuses = [10, 11, 12, 22, 5, 0, 1, 21, 20];

/** 只有审批流程（商业版）才会产生的状态：社区版里没有文章处于这些状态时不显示对应页签 */
export const reviewStatuses = [11, 12, 22];

/**
 * 可提交：草稿、已退回、已下线、已归档。待审核的文章名下有流程实例时不可提交——流程还在进行，再点提交什么也不会发生
 * （列表行不带栏目的流程标识，以文章自己的 processInstanceId 为准）；没有流程实例、或没有流程引擎（社区版）的
 * 待审核文章可以提交。已删除的文章先恢复为草稿再提交
 */
export const canSubmit = (row: ArticleStatusRow): boolean =>
  [1, 10, 21, 22].includes(row.status) || (row.status === 11 && (!row.processInstanceId || currentUser.commercialTier < 1));

/**
 * 可退回：待审核且没有流程实例——会员投稿到没有审批流程的栏目时停在这个状态（docs/spec/member-contribute/plan.md D6），
 * “通过”就是提交，“退回”走文章管理自己的退回接口；进入了审批流程的由“文章审核”退回
 */
export const canReject = (row: ArticleStatusRow): boolean => row.status === 11 && !row.processInstanceId;

/** 可归档：已发布 */
export const canArchive = (row: ArticleStatusRow): boolean => row.status === 0;

/** 可下线：已发布、已归档、待发布、待审核、审核中 */
export const canOffline = (row: ArticleStatusRow): boolean => [0, 1, 5, 11, 12].includes(row.status);

/** 可删除（放入回收站）：已删除以外的 */
export const canDelete = (row: ArticleStatusRow): boolean => row.status !== 20;

/** 可恢复：回收站里的。恢复为草稿 */
export const canRestore = (row: ArticleStatusRow): boolean => row.status === 20;

export type ArticleStatusAction = 'submit' | 'archive' | 'offline' | 'delete' | 'restore' | 'completelyDelete';

/** 各操作适用于哪些文章。彻底删除适用于任何状态（有意如此：误发的内容要能一步清除） */
export const articleActionRules: Record<ArticleStatusAction, (row: ArticleStatusRow) => boolean> = {
  submit: canSubmit,
  archive: canArchive,
  offline: canOffline,
  delete: canDelete,
  restore: canRestore,
  completelyDelete: () => true,
};

/** 各操作所需的权限。恢复与删除同权：能放进回收站的人就能取回来 */
export const articleActionPerms: Record<ArticleStatusAction, string> = {
  submit: 'article:submit',
  archive: 'article:archive',
  offline: 'article:offline',
  delete: 'article:delete',
  restore: 'article:delete',
  completelyDelete: 'article:completelyDelete',
};

/** 各操作的文案键 */
export const articleActionLabels: Record<ArticleStatusAction, string> = {
  submit: 'article.op.submit',
  archive: 'article.op.archive',
  offline: 'article.op.offline',
  delete: 'delete',
  restore: 'article.op.restore',
  completelyDelete: 'article.op.completelyDelete',
};

/**
 * 行内直接给出的那一个状态操作：回收站里是恢复；在线上的（已发布、已归档）或排着队要上线的（待发布）是下线；
 * 还没上线的（草稿、已退回、已下线）是提交；在审核中的只能下线。已归档文章的“提交”（重新发布）收在“更多”里
 */
export const primaryStatusAction = (row: ArticleStatusRow): 'restore' | 'submit' | 'offline' | undefined => {
  if (canRestore(row)) return 'restore';
  if ([0, 1, 5].includes(row.status)) return 'offline';
  if (canSubmit(row)) return 'submit';
  if (canOffline(row)) return 'offline';
  return undefined;
};
