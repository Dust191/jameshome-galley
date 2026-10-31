import js from '@eslint/js'
import globals from 'globals'
import tseslint from 'typescript-eslint'

// 分工：Prettier 管格式，ESLint 管正确性（未使用变量、显式 any、悬浮 Promise 等）
export default [
  { ignores: ['dist', 'node_modules', 'coverage', 'playwright-report'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/consistent-type-imports': 'warn',
    },
  },
]
