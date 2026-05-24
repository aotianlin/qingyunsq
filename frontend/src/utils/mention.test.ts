import { describe, expect, it } from 'vitest';
import { parseMentions } from './mention';

/**
 * mention.ts 单元测试（任务 T8.9 / 漏洞 18）。
 *
 * <p>原 renderMentions 已删除（生成的 HTML 字符串需要 v-html 渲染，
 * 让恶意 nickname 直接进入 DOM）。此处只测试 parseMentions 的"片段拆分"
 * 行为；安全性由组件 {@code MentionText.vue} 通过 mustache 插值与
 * {@code RouterLink} 的 to 对象传参共同保障，不在本文件覆盖。</p>
 */
describe('parseMentions', () => {
  it('返回空数组：输入为空字符串', () => {
    expect(parseMentions('')).toEqual([]);
  });

  it('单段普通文本：保留为单个 segment 且 mention 字段缺失', () => {
    const result = parseMentions('今天没有提到任何人');
    expect(result).toHaveLength(1);
    expect(result[0].text).toBe('今天没有提到任何人');
    expect(result[0].mention).toBeUndefined();
  });

  it('英文 mention：被识别为 segment 且 mention=用户名', () => {
    const result = parseMentions('请 @alice 看一下');
    // 期望：["请 ", "@alice", " 看一下"]
    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({ text: '请 ' });
    expect(result[1]).toEqual({ text: '@alice', mention: 'alice' });
    expect(result[2]).toEqual({ text: ' 看一下' });
  });

  it('中文 + 数字 + 连字符 nickname：mention 字段保留完整用户名', () => {
    const result = parseMentions('请 @张三-2024 评论');
    expect(result).toHaveLength(3);
    expect(result[1]).toEqual({ text: '@张三-2024', mention: '张三-2024' });
  });

  it('多个 mention 夹杂普通文本：按出现顺序拆分', () => {
    const result = parseMentions('Hi @bob 与 @cathy，请看');
    expect(result.map((seg) => seg.text)).toEqual([
      'Hi ',
      '@bob',
      ' 与 ',
      '@cathy',
      '，请看',
    ]);
    expect(result[1].mention).toBe('bob');
    expect(result[3].mention).toBe('cathy');
  });

  it('恶意 nickname 不会被解释为 HTML：text 字段原样保留 < > 等字符', () => {
    // 关键安全保证：parseMentions 不会在 text / mention 字段里塞入任何 HTML
    // 标签；前端只通过 mustache 插值渲染，由 Vue 自动 HTML 转义。
    const result = parseMentions('hello <script>alert(1)</script>');
    expect(result).toHaveLength(1);
    expect(result[0].text).toBe('hello <script>alert(1)</script>');
    expect(result[0].mention).toBeUndefined();
  });
});
