import { currentUser, hasPermission } from '@/stores/useCurrentUser';
import { queryArticlePendingCount, queryArticleRejectCount, queryFormPendingCount, queryFormRejectCount } from '@/api/content';
import {
  queryCommentUnreviewedCount,
  queryMessageBoardUnreviewedCount,
  queryDisclosurePendingCount,
  queryMailboxPendingCount,
  queryInterviewQuestionPendingCount,
} from '@/api/interaction';
import { isMenuRouteVisible } from './menuLeaves';

/**
 * 首页待办的九个队列（docs/spec/console-home）。每个队列 = 计数接口 + 权限 + 级别 + 落点。
 * 权限与级别分别判定：待审计数走 review 权限（articleReview:list / formReview:list），被退回计数走 list 权限（按当前用户统计）。
 * 级别与 Commercial.PERMISSION_TIERS 一致：工作流（文章审核）T1，自定义表单 T2，政务四项 T4；留言、评论为核心功能。
 */
export interface TodoQueue {
  key: string;
  labelKey: string;
  permission: string;
  tier: number;
  /** 静默计数（quiet = true 跳过全局错误弹窗）：计数接口与目标列表同权限，出错只可能是后端故障，
   * 首页是登录后第一屏，不该为此弹满屏堆栈；同一故障会在对应列表页再现。失败的队列不出胶囊（TodoCard） */
  fetch: () => Promise<unknown>;
  to: { name: string; query?: Record<string, number> };
}

export const TODO_QUEUES: TodoQueue[] = [
  { key: 'pendingArticle', labelKey: 'todo.pendingArticle', permission: 'articleReview:list', tier: 1, fetch: () => queryArticlePendingCount(undefined, true), to: { name: 'ArticleReviewList' } },
  { key: 'rejectedArticle', labelKey: 'todo.rejectedArticle', permission: 'article:list', tier: 1, fetch: () => queryArticleRejectCount(undefined, true), to: { name: 'ArticleList', query: { status: 22 } } },
  { key: 'pendingForm', labelKey: 'todo.pendingForm', permission: 'formReview:list', tier: 2, fetch: () => queryFormPendingCount(undefined, true), to: { name: 'FormReviewList' } },
  { key: 'rejectedForm', labelKey: 'todo.rejectedForm', permission: 'form:list', tier: 2, fetch: () => queryFormRejectCount(undefined, true), to: { name: 'FormList', query: { status: 22 } } },
  { key: 'unreviewedMessageBoard', labelKey: 'todo.unreviewedMessageBoard', permission: 'messageBoard:list', tier: 0, fetch: () => queryMessageBoardUnreviewedCount(undefined, true), to: { name: 'MessageBoardList', query: { status: 1 } } },
  { key: 'unreviewedComment', labelKey: 'todo.unreviewedComment', permission: 'comment:list', tier: 0, fetch: () => queryCommentUnreviewedCount(undefined, true), to: { name: 'CommentList', query: { status: 1 } } },
  { key: 'pendingDisclosure', labelKey: 'todo.pendingDisclosure', permission: 'disclosure:list', tier: 4, fetch: () => queryDisclosurePendingCount(undefined, true), to: { name: 'DisclosureList', query: { status: 0 } } },
  { key: 'pendingMailbox', labelKey: 'todo.pendingMailbox', permission: 'mailbox:list', tier: 4, fetch: () => queryMailboxPendingCount(undefined, true), to: { name: 'MailboxList', query: { status: 0 } } },
  { key: 'pendingInterviewQuestion', labelKey: 'todo.pendingInterviewQuestion', permission: 'interviewQuestion:list', tier: 4, fetch: () => queryInterviewQuestionPendingCount(undefined, true), to: { name: 'InterviewList' } },
];

/** 有权限、级别够，且落点在侧栏可见（只有 list 权限而无 page 标志的角色点了会被路由守卫弹回，不如不显示） */
export const isQueueAllowed = (queue: TodoQueue): boolean => hasPermission(queue.permission) && currentUser.commercialTier >= queue.tier && isMenuRouteVisible(queue.to.name);
