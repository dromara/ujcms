/**
 * 引用字段（articleRef/channelRef/blockRef/voteRef）存储值 → 选择器可匹配的字符串ID。
 *
 * 存储值有四种形态（component-settings §4.1）：数字（小ID的既存值以 JSON 数字回流；雪花ID经
 * Long→String 序列化为字符串）、数字串、{id:…}、{alias:…}（种子缺省值形态）。选择器选项值
 * 来自列表接口，恒为字符串ID——匹配前须归一，否则显示裸值或空白。
 */
export const refId = (value: unknown): string | undefined => {
  if (value == null) return undefined;
  if (typeof value === 'number' || typeof value === 'string') return String(value);
  if (typeof value === 'object' && (value as any).id != null) return String((value as any).id);
  return undefined;
};

/**
 * 别名形态（{alias:…}）的别名；其余形态返回 undefined。ID 由各选择器按已加载目录解析
 * （与服务端按站点可见范围解析同一目录口径）。
 */
export const refAlias = (value: unknown): string | undefined => {
  if (typeof value === 'object' && value != null && typeof (value as any).alias === 'string') {
    return (value as any).alias;
  }
  return undefined;
};
