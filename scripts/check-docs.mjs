#!/usr/bin/env node
// 文档 CI 校验（GOV-000 §4.4.5）。零依赖，只做结构检查，不判断内容好坏。
// 校验项：front-matter 字段齐全、status 取值合法、doc_id 唯一、related 目标存在、
//        iter 指向的迭代记录存在、索引已登记、用词反查（禁用写法失败 / 易混提示告警）、表格内空行。
// 用法：node scripts/check-docs.mjs [--fix-hint]

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')
const DOCS = join(ROOT, 'docs')
const ITER_DIR = join(DOCS, '07-iteration')

const REQUIRED = [
  'title',
  'doc_id',
  'status',
  'owner',
  'reviewers',
  'created',
  'updated',
  'next_review',
  'iter',
  'related',
]
const STATUS = new Set(['draft', 'in_review', 'accepted', 'rejected', 'approved'])
// 不在仓库内的文档，related 允许引用但不校验文件存在
const EXTERNAL_IDS = new Set(['GOV-000', 'CONTRIBUTING', 'GOVERNANCE', 'SECURITY'])

// ITER 结构校验（GOV-007 M-01）。轮次信息是第一节且字段固定，所有记录一律硬性；
// 六节结构自 ITER-20261112-10 起硬性——更早的记录章节命名不一（改动/验证/遗留等），
// 只作历史，不回头改，也不刷告警。
const ITER_STRUCTURE_FROM = 'ITER-20261112-10'
const ITER_SECTIONS = [
  '轮次信息',
  '变更清单',
  '变更过程',
  '影响面',
  '开发者审定回顾',
  '与上轮的差异摘要',
]
const ITER_META_FIELDS = ['编号', '日期', '触发来源', '涉及版本', '参与模型', '开发者']

const errors = []
const warns = []

function walk(dir) {
  const out = []
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) {
      if (name === '_templates') continue
      out.push(...walk(p))
    } else if (name.endsWith('.md')) {
      out.push(p)
    }
  }
  return out
}

function parseFrontMatter(text, file) {
  // 统一行尾与 BOM：Windows 上手工编辑过的文件常带 CRLF
  text = text.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n')
  if (!text.startsWith('---\n')) {
    errors.push(`${file}: 缺少 front-matter`)
    return null
  }
  const end = text.indexOf('\n---', 3)
  if (end < 0) {
    errors.push(`${file}: front-matter 未闭合`)
    return null
  }
  const raw = text.slice(4, end)
  const meta = {}
  for (const line of raw.split('\n')) {
    const m = line.match(/^([a-z_]+):\s*(.*)$/)
    if (m) meta[m[1]] = m[2].trim().replace(/^"(.*)"$/, '$1')
  }
  return { meta, body: text.slice(end + 4) }
}

const files = walk(DOCS).filter((f) => relative(DOCS, f) !== 'idea.md')
const docs = []

for (const f of files) {
  const rel = relative(ROOT, f).replace(/\\/g, '/')
  const parsed = parseFrontMatter(readFileSync(f, 'utf8'), rel)
  if (!parsed) continue
  const { meta, body } = parsed

  for (const k of REQUIRED) {
    if (!meta[k]) errors.push(`${rel}: front-matter 缺少 ${k}`)
  }
  if (meta.status && !STATUS.has(meta.status)) {
    errors.push(`${rel}: status 取值非法（${meta.status}），允许 ${[...STATUS].join(' / ')}`)
  }
  docs.push({ rel, file: f, meta, body })
}

// doc_id 唯一
const byId = new Map()
for (const d of docs) {
  if (!d.meta.doc_id) continue
  if (byId.has(d.meta.doc_id)) {
    errors.push(`doc_id 重复：${d.meta.doc_id}（${byId.get(d.meta.doc_id).rel} 与 ${d.rel}）`)
  } else {
    byId.set(d.meta.doc_id, d)
  }
}

const indexText = readFileSync(join(DOCS, '00-index.md'), 'utf8')

for (const d of docs) {
  const id = d.meta.doc_id
  if (!id) continue

  // 索引登记
  if (!indexText.includes(id)) errors.push(`${d.rel}: 未在 00-index.md 登记（${id}）`)

  // related 目标存在
  for (const target of (d.meta.related || '').replace(/[\[\]]/g, '').split(',')) {
    const t = target.trim()
    if (!t) continue
    if (EXTERNAL_IDS.has(t)) continue
    if (!byId.has(t)) errors.push(`${d.rel}: related 指向不存在的文档 ${t}`)
  }

  // iter 指向的迭代记录存在
  const iter = d.meta.iter
  if (iter && !/^(未确认|N\/A)$/.test(iter)) {
    const p = join(ITER_DIR, `${iter}.md`)
    try {
      statSync(p)
    } catch {
      errors.push(`${d.rel}: iter 指向的迭代记录不存在 ${iter}`)
    }
  }

  // ITER 结构（GOV-007 M-01）
  if (d.rel.startsWith('07-iteration/') || d.rel.includes('/07-iteration/')) {
    const heads = [...d.body.matchAll(/^## (.+)$/gm)].map((m) => m[1])
    const titles = heads.map((h) => h.replace(/^[一二三四五六七八九十]+、/, '').trim())

    if (titles[0] !== '轮次信息') {
      errors.push(`${d.rel}: 第一节必须是「轮次信息」，实际为「${titles[0] || '空'}」`)
    } else {
      // 轮次信息区块：从该标题到下一个 ## 之间
      const start = d.body.indexOf('## ' + heads[0])
      const rest = d.body.slice(start + heads[0].length)
      const nextHead = rest.search(/^## /m)
      const metaBlock = nextHead < 0 ? rest : rest.slice(0, nextHead)
      const absent = ITER_META_FIELDS.filter((f) => !new RegExp(`^\\| *${f} *\\|`, 'm').test(metaBlock))
      if (absent.length) {
        errors.push(`${d.rel}: 轮次信息缺字段 ${absent.join('、')}`)
      }
    }

    if (id >= ITER_STRUCTURE_FROM) {
      const missing = ITER_SECTIONS.filter((t) => !titles.includes(t))
      if (missing.length) {
        errors.push(`${d.rel}: 缺必填章节 ${missing.join('、')}（DEV-008 §2.7）`)
      }
    }
  }

  // 表格内空行（会断开表格）
  const lines = d.body.split('\n')
  for (let i = 1; i < lines.length - 1; i++) {
    if (lines[i].trim() === '' && lines[i - 1].startsWith('|') && lines[i + 1].startsWith('|')) {
      errors.push(`${d.rel}: 第 ${i + 1} 行附近表格被空行断开`)
    }
  }
}

// 用词反查（RFC-0001）：禁用写法 → 失败；易混提示 → 告警。
// 词条表列序：中文 | 英文 | 定义 | 域 | 禁用写法 | 易混提示 | 状态 | 引入版本 | 示例
const glossary = readFileSync(join(DOCS, '00-glossary.md'), 'utf8')
const banned = []
const mixed = []
const terms = []

const splitWords = (cell) =>
  cell
    .split(/[、,，]/)
    .map((w) => w.trim())
    .filter((w) => w.length >= 2 && !w.includes('（'))

for (const line of glossary.split('\n')) {
  if (!line.startsWith('| ')) continue
  const cells = line.split('|').map((c) => c.trim())
  if (cells.length < 10) continue
  if (!/^(active|proposed|deprecated)$/.test(cells[7])) continue
  if (cells[1].length >= 2) terms.push(cells[1])
  if (cells[7] === 'deprecated') continue
  banned.push(...splitWords(cells[5]))
  mixed.push(...splitWords(cells[6]))
}
terms.sort((a, b) => b.length - a.length)

const optOut = []
for (const d of docs) {
  if (d.rel.endsWith('00-glossary.md')) continue // glossary 自身要写禁用写法
  // 显式豁免：正文需逐条引用禁用写法做分析时使用，须在豁免标记旁写明理由
  if (d.body.includes('term-check: off')) {
    optOut.push(d.rel)
    continue
  }
  let body = d.body.replace(/```[\s\S]*?```/g, '')
  for (const t of terms) body = body.split(t).join('□'.repeat(t.length))
  const hitBanned = banned.filter((w) => body.includes(w))
  const hitMixed = mixed.filter((w) => body.includes(w))
  // RFC-0001 R-3：ITER 为历史记录（只增不改），命中禁用写法降为告警
  const isIter = d.rel.startsWith('docs/07-iteration/')
  if (hitBanned.length) {
    const msg = `${d.rel}: 出现禁用写法 ${[...new Set(hitBanned)].join('、')}`
    if (isIter) warns.push(`${msg}（历史记录，仅提示）`)
    else errors.push(msg)
  }
  if (hitMixed.length) warns.push(`${d.rel}: 疑用近义词 ${[...new Set(hitMixed)].join('、')}`)
}

const mode = process.argv.includes('--warn-only') ? 'warn' : 'strict'

for (const f of optOut) console.log(`[INFO] ${f}: 已声明 term-check: off，跳过用词反查`)
for (const w of warns) console.log(`[WARN] ${w}`)
for (const e of errors) console.log(`[FAIL] ${e}`)

if (errors.length === 0) {
  console.log(
    `文档校验通过：${docs.length} 份，doc_id 唯一，front-matter 齐全，索引与 related 一致；禁用写法零命中，易混提示告警 ${warns.length} 条。`,
  )
  process.exit(0)
}
console.log(`\n共 ${errors.length} 处错误，${warns.length} 条告警。`)
process.exit(mode === 'warn' ? 0 : 1)
