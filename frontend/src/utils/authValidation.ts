export type PasswordStrength = 'empty' | 'weak' | 'medium' | 'strong';

export interface PasswordStrengthResult {
  strength: PasswordStrength;
  label: string;
  hint: string;
  score: number;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CHINESE_PATTERN = /[\u4e00-\u9fff]/;
const NUMBER_PATTERN = /^\d+$/;

export function validateNickname(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '请输入昵称';
  if (trimmed.length > 12) return '昵称不能超过 12 个字符';
  return '';
}

export function validateStudentNo(value: string, required = false): string {
  const trimmed = value.trim();
  if (!trimmed) return required ? '请输入学号' : '';
  if (!NUMBER_PATTERN.test(trimmed)) return '学号只能包含数字';
  if (trimmed.length !== 12) return '学号必须是 12 位数字';
  return '';
}

export function validateEmail(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '请输入邮箱';
  if (CHINESE_PATTERN.test(trimmed)) return '邮箱不能包含中文字符';
  if (trimmed.length < 6 || trimmed.length > 128) return '邮箱长度需为 6-128 个字符';
  if (!EMAIL_PATTERN.test(trimmed)) return '请输入有效的邮箱地址';
  return '';
}

export function validatePassword(value: string): string {
  if (!value) return '请输入密码';
  if (value.length < 8 || value.length > 64) return '密码长度需为 8-64 位';
  if (!/(?=.*[A-Za-z])(?=.*\d)/.test(value)) return '密码必须同时包含字母和数字';
  return '';
}

export function validateConfirmPassword(value: string, password: string): string {
  if (!value) return '请再次输入密码';
  if (value !== password) return '两次密码不一致';
  return '';
}

export function getPasswordStrength(value: string): PasswordStrengthResult {
  if (!value) {
    return {
      strength: 'empty',
      label: '未输入',
      hint: '请输入 8-64 位密码',
      score: 0,
    };
  }

  let score = 0;
  if (value.length >= 8) score += 1;
  if (value.length >= 12) score += 1;
  if (/[a-z]/.test(value)) score += 1;
  if (/[A-Z]/.test(value)) score += 1;
  if (/\d/.test(value)) score += 1;
  if (/[^A-Za-z0-9]/.test(value)) score += 1;

  if (value.length < 8) {
    return {
      strength: 'weak',
      label: '弱',
      hint: '至少输入 8 位密码',
      score: Math.max(score, 1),
    };
  }

  if (score >= 5) {
    return {
      strength: 'strong',
      label: '强',
      hint: '密码强度良好',
      score,
    };
  }

  if (score >= 3) {
    return {
      strength: 'medium',
      label: '中',
      hint: '建议加入大小写字母、数字或符号',
      score,
    };
  }

  return {
    strength: 'weak',
    label: '弱',
    hint: '建议混合字母、数字或符号',
    score,
  };
}
