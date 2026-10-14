import { ElMessageBox } from 'element-plus';

/** 审核拒绝原因的长度上限，与服务端 RejectParams 的 @Length(max = 300) 一致 */
export const REJECT_REASON_MAX = 300;

/**
 * 询问审核拒绝的原因：必填，不超过 {@link REJECT_REASON_MAX} 字。文章审核、表单审核的四处入口共用。
 *
 * @returns 去掉首尾空白的原因；取消时为 undefined
 */
export const promptRejectReason = async (t: (key: string, params?: Record<string, unknown>) => string): Promise<string | undefined> => {
  try {
    const { value } = await ElMessageBox.prompt(t('review.rejectReason'), t('review.reject'), {
      confirmButtonText: t('submit'),
      cancelButtonText: t('cancel'),
      inputType: 'textarea',
      inputValidator: (input: string) => {
        const text = input?.trim() ?? '';
        if (!text) {
          return t('v.required');
        }
        if (text.length > REJECT_REASON_MAX) {
          return t('v.maxLength', { maxLength: REJECT_REASON_MAX });
        }
        return true;
      },
    });
    return value.trim();
  } catch {
    return undefined;
  }
};
