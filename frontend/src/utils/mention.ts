/**
 * 解析文本中的 @username 提及。
 *
 * <p>对应 bugfix.md 漏洞 18 / T8.9：原 {@code renderMentions} 用字符串拼接
 * 返回 HTML 字符串，调用方需要 {@code v-html} 渲染，攻击者可在 nickname 中
 * 注入 {@code <script>} / {@code onerror} 突破净化。改造后只暴露
 * {@code parseMentions} 返回结构化片段，由 Vue 组件 {@code MentionText.vue}
 * 用 {@code <RouterLink>} + mustache 文本插值渲染，杜绝 {@code v-html}。</p>
 *
 * 匹配规则：@ 后跟中文 / 英文 / 数字 / 下划线 / 连字符，1-30 字符。
 */
const MENTION_RE = /@([\w一-龥-]{1,30})/g;

export interface MentionSegment {
  /** 当前片段在原文里的字符串（用作 mustache 插值，永远经 Vue 自动转义）。 */
  text: string;
  /**
   * 当前片段的 mention 用户名（不含 @ 前缀）。
   * 仅当本片段是一段被识别出的 mention 时存在；普通文本片段为空。
   */
  mention?: string;
}

/**
 * 把文本拆分为 "普通文本" 与 "mention 命中" 两类片段。
 *
 * <p>命中片段保留 {@code text}（含 {@code @} 前缀，用于显示）+ {@code mention}
 * （仅用户名，用于跳转 URL）；普通片段只保留 {@code text}。</p>
 */
export function parseMentions(text: string): MentionSegment[] {
  if (!text) return [];

  MENTION_RE.lastIndex = 0;
  const segments: MentionSegment[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = MENTION_RE.exec(text)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ text: text.slice(lastIndex, match.index) });
    }
    segments.push({ text: match[0], mention: match[1] });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    segments.push({ text: text.slice(lastIndex) });
  }

  return segments;
}

// 漏洞 18 / T8.9：原导出的 renderMentions(text): string 已删除。
// 该函数会拼接 HTML 字符串，调用方必须用 v-html 渲染，让恶意 nickname
// 中的 <script> / onerror / javascript: URL 直接进入 DOM。
// 替代方案：组件 MentionText.vue 通过 parseMentions + <RouterLink> + mustache
// 文本插值渲染，所有用户内容都经 Vue 自动转义，不再可能触发 XSS。
