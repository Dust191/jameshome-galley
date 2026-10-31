// 提交信息规则。本地钩子（Husky 或等价机制）与 CI 共用同一份配置。
module.exports = {
  rules: {
    'type-empty': [2, 'never'],
    'type-enum': [
      2,
      'always',
      ['feat', 'fix', 'refactor', 'perf', 'docs', 'test', 'build', 'ci', 'chore', 'revert'],
    ],
    'scope-empty': [2, 'never'],
    'scope-enum': [
      2,
      'always',
      ['api', 'web', 'db', 'auth', 'material', 'stock', 'stats', 'infra', 'docs', 'deps', 'release'],
    ],
    'subject-empty': [2, 'never'],
    'subject-max-length': [2, 'always', 50],
    'subject-full-stop': [2, 'never', '。'],
    'header-max-length': [2, 'always', 72],
  },
}
