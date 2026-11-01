#!/usr/bin/env node
// 文档 CI 校验（GOV-000 §4.4.5）。零依赖，只做结构检查，不判断内容好坏。
// 校验项：front-matter 字段齐全、status 取值合法、doc_id 唯一、related 目标存在、
//        iter 指向的迭代记录存在、索引已登记、禁用写法反查、表格内空行。
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
const EXTERNAL_IDS = new Set(['GOV-000'])

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

  // 表格内空行（会断开表格）
  const lines = d.body.split('\n')
  for (let i = 1; i < lines.length - 1; i++) {
    if (lines[i].trim() === '' && lines[i - 1].startsWith('|') && lines[i + 1].startsWith('|')) {
      errors.push(`${d.rel}: 第 ${i + 1} 行附近表格被空行断开`)
    }
  }
}

// 禁用写法反查（仅扫描正文，跳过代码块）
const glossary = readFileSync(join(DOCS, '00-glossary.md'), 'utf8')
const banned = []
for (const line of glossary.split('\n')) {
  if (!line.startsWith('| ')) continue
  const cells = line.split('|').map((c) => c.trim())
  if (cells.length < 9) continue
  if (cells[6] === '中文词条' || cells[6].startsWith('-')) continue
  if (!/^(active|proposed|deprecated)$/.test(cells[6])) continue
  const status = cells[6]
  if (status === 'deprecated') continue
  for (const w of cells[5].split(/[、,，]/)) {
    const t = w.trim()
    if (t.length < 2) continue
    if (t.includes('（')) continue // 括号里是说明，不是词本身
    banned.push(t)
  }
}

for (const d of docs) {
  if (d.rel.endsWith('00-glossary.md')) continue // glossary 自身要写禁用写法
  const body = d.body.replace(/```[\s\S]*?```/g, '')
  const hits = banned.filter((w) => body.includes(w))
  if (hits.length) warns.push(`${d.rel}: 出现禁用写法 ${[...new Set(hits)].join('、')}`)
}

const mode = process.argv.includes('--warn-only') ? 'warn' : 'strict'

for (const w of warns) console.log(`[WARN] ${w}`)
for (const e of errors) console.log(`[FAIL] ${e}`)

if (errors.length === 0) {
  console.log(
    `文档校验通过：${docs.length} 份，doc_id 唯一，front-matter 齐全，索引与 related 一致；禁用写法告警 ${warns.length} 条。`,
  )
  process.exit(0)
}
console.log(`\n共 ${errors.length} 处错误，${warns.length} 条告警。`)
process.exit(mode === 'warn' ? 0 : 1)
