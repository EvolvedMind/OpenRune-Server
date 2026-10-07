#!/usr/bin/env node
// Offline content index and roadmap renderer. This does not verify gameplay.
// node tools/progress/content-progress.mjs
// node tools/progress/content-progress.mjs --check   # no writes
// node tools/progress/content-progress.mjs --fetch-wiki   # explicit catalog refresh

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const P = (...parts) => path.join(ROOT, ...parts)
const rel = (file) => path.relative(ROOT, file).split(path.sep).join('/')
const norm = (value) => value.toLowerCase().replace(/[^a-z0-9]/g, '')
const cell = (value) => String(value ?? '').replace(/\|/g, '&#124;').replace(/\s+/g, ' ').trim()
const README_MARKERS = ['<!-- content-progress:start -->', '<!-- content-progress:end -->']
const ROADMAP_MARKERS = ['<!-- roadmap:start -->', '<!-- roadmap:end -->']
const WIKI_FILE = 'tools/progress/wiki-cache.json'
const API = 'https://oldschool.runescape.wiki/api.php'

function walk(dir, accept, out = []) {
  if (!fs.existsSync(dir)) return out
  for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name !== 'build' && entry.name !== '.git') walk(file, accept, out)
    } else if (accept(entry.name)) out.push(file)
  }
  return out
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(P(file), 'utf8'))
}

function checkMarkers(text, markers, file) {
  const [start, end] = markers
  if (text.split(start).length !== 2 || text.split(end).length !== 2 || text.indexOf(start) > text.indexOf(end)) {
    throw new Error(`${file}: expected exactly one ordered ${start} / ${end} pair`)
  }
}

function replaceBlock(text, markers, block) {
  const [start, end] = markers
  const eol = text.includes('\r\n') ? '\r\n' : '\n'
  const body = (start + '\n\n' + block.trim() + '\n\n' + end).replace(/\r?\n/g, eol)
  return text.slice(0, text.indexOf(start)) + body + text.slice(text.indexOf(end) + end.length)
}

// A module is evidence of source structure, not an implemented encounter.
function listModules(root = 'content') {
  return walk(P(root), (name) => name === 'build.gradle.kts').map((build) => {
    const dir = path.dirname(build)
    return {
      path: rel(dir),
      hasSource: walk(path.join(dir, 'src', 'main'), (name) => name.endsWith('.kt')).length > 0,
    }
  }).sort((a, b) => a.path.localeCompare(b.path))
}

function loadNpcs() {
  const file = P('osrs-dumps', 'dump.npc')
  const byName = new Map()
  const symbols = []
  if (!fs.existsSync(file)) return { byName, symbols }
  let current = null
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const symbol = line.match(/^\[([a-z0-9_]+)\]$/)
    if (symbol) { current = symbol[1]; symbols.push(current); continue }
    const name = line.match(/^name=(.+)$/)
    if (!name || !current) continue
    const key = name[1].trim().toLowerCase()
    if (!byName.has(key)) byName.set(key, [])
    byName.get(key).push(current)
    current = null
  }
  return { byName, symbols }
}

function scanReferences() {
  const references = new Map()
  const symbols = /\b(npc|content)\.([a-z0-9_]+)/g
  const behaviour = /\bon[A-Z][A-Za-z0-9]*\(|PluginScript\b|\bencounter\(/
  for (const file of walk(P('content'), (name) => name.endsWith('.kt'))) {
    const name = rel(file)
    if (!name.includes('/src/main/') || name.startsWith('content/drops/')) continue
    const text = fs.readFileSync(file, 'utf8')
    if (!behaviour.test(text)) continue
    for (const match of text.matchAll(symbols)) {
      const key = match[1] + '.' + match[2]
      if (!references.has(key)) references.set(key, new Set())
      references.get(key).add(name)
    }
  }
  return references
}

function loadDropTables() {
  const tables = new Map()
  for (const file of walk(P('content', 'drops', 'src'), (name) => name.endsWith('.toml') || name.endsWith('DropTable.kt'))) {
    const key = norm(path.basename(file).replace(/DropTable\.kt$|\.toml$/, ''))
    if (!tables.has(key)) tables.set(key, [])
    tables.get(key).push(rel(file))
  }
  return tables
}

function findModule(title, moduleRoot, modules, aliases) {
  const candidates = modules.filter((module) => module.path.startsWith(moduleRoot + '/'))
  const leaf = (module) => norm(module.path.split('/').pop())
  const wanted = aliases[title] ? norm(aliases[title]) : norm(title)
  return candidates.find((module) => leaf(module) === wanted)
    ?? (aliases[title] ? null : candidates.find((module) => leaf(module).length >= 4 && wanted.includes(leaf(module))))
    ?? null
}

function inspectFeature(feature, ctx) {
  const modulePath = feature.module ?? feature.engine
  const matches = feature.engine ? ctx.engineModules.get(feature.engine) ?? []
    : modulePath ? ctx.modules.filter((module) => module.path === modulePath || module.path.startsWith(modulePath + '/')) : []
  const hasModule = matches.some((module) => module.hasSource)
  const found = new Set()
  const npcSymbols = feature.rscmPrefixes?.length
    ? ctx.npcs.symbols.filter((symbol) => feature.rscmPrefixes.some((prefix) => symbol.startsWith(prefix)))
    : ctx.npcs.byName.get(feature.name.toLowerCase()) ?? []
  for (const symbol of [...npcSymbols.map((name) => 'npc.' + name), ...(feature.contentTags ?? []).map((name) => 'content.' + name)]) {
    for (const file of ctx.references.get(symbol) ?? []) found.add(file)
  }
  const drops = ctx.dropTables.get(norm(feature.name)) ?? []
  let evidence = [], status = 'Not detected'
  if (hasModule) {
    status = 'Module'
    evidence = [modulePath]
  } else if (matches.length) {
    status = 'Module stub'
    evidence = [modulePath]
  } else if (found.size) {
    status = 'References only'
    evidence = [...found].sort().slice(0, 2)
  } else if (drops.length) {
    status = 'Drop table only'
    evidence = drops.slice(0, 2)
  }
  return { ...feature, status, evidence, hasModule, modulePaths: matches.map((module) => module.path) }
}

function wikiUrl(title) {
  return 'https://oldschool.runescape.wiki/w/' + encodeURIComponent(title.replace(/ /g, '_'))
}

function evidenceLink(file, modules) {
  const owner = modules.filter((module) => file.startsWith(module.path + '/')).sort((a, b) => b.path.length - a.path.length)[0]
  return '[' + (owner ? path.posix.basename(file) : file) + '](' + file + ')'
}

function renderFeatureTable(features, modules) {
  const lines = ['| Feature | Evidence | Source |', '|---|---|---|']
  for (const feature of features) {
    const label = feature.wiki ? '[' + cell(feature.name) + '](' + wikiUrl(feature.wiki) + ')' : cell(feature.name)
    lines.push('| ' + label + ' | ' + feature.status + ' | ' + (feature.evidence.map((file) => evidenceLink(file, modules)).join('; ') || '—') + ' |')
  }
  return lines.join('\n')
}

function renderInventory(categories, modules) {
  const lines = [
    '# Content inventory', '',
    'Generated technical index. [PROGRESS.md](PROGRESS.md) contains current status and the roadmap.', '',
    '**Module** = source module found; **Module stub** = module without Kotlin source in `src/main`.',
    '**References only** = symbol mentioned in other code; **Drop table only** = only a loot definition found.',
    '**Not detected** = no match in this scan. None of these labels verifies gameplay or completeness.', '',
    'The catalog matches stored wiki lists against module names/aliases and NPC/content symbols. References may be pet or shop code; unmatched implementations may exist elsewhere.',
    'Edit source/configuration, then run `node tools/progress/content-progress.mjs`.', '',
  ]
  for (const category of categories) {
    lines.push('## ' + category.name, '')
    const modulesFound = category.features.filter((feature) => feature.hasModule)
    const other = category.features.filter((feature) => !feature.hasModule)
    if (modulesFound.length) lines.push(renderFeatureTable(modulesFound, modules), '')
    if (other.length) {
      lines.push('<details>', '<summary>Other catalog entries (' + other.length + ')</summary>', '',
        renderFeatureTable(other, modules), '', '</details>', '')
    }
  }
  const covered = new Set(categories.flatMap((category) => category.features.flatMap((feature) => feature.modulePaths)))
  const otherModules = modules.filter((module) => !covered.has(module.path))
  if (otherModules.length) {
    lines.push('## Other content modules', '', '<details>', '<summary>Modules outside the catalog matches</summary>', '', '| Module |', '|---|')
    for (const module of otherModules) lines.push('| [' + module.path + '](' + module.path + ') |')
    lines.push('', '</details>', '')
  }
  lines.push('Catalog: [OSRS Wiki](https://oldschool.runescape.wiki/), [CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).', '')
  return lines.join('\n')
}

function renderRoadmap(roadmap) {
  const categories = roadmap.categories ?? {}
  const seen = new Set()
  for (const item of roadmap.items ?? []) {
    const key = norm(String(item.title ?? '').replace(/\s*[—–-]\s*Complete$/i, ''))
    if (!key || seen.has(key)) throw new Error('roadmap.json: missing or duplicate title: ' + item.title)
    if (!categories[item.category]) throw new Error('roadmap.json: unknown category: ' + item.category)
    if (!roadmap.statuses?.[item.status]?.icon) throw new Error('roadmap.json: missing status icon: ' + item.status)
    seen.add(key)
  }
  const lines = ['Requirements and details: [roadmap.json](tools/progress/roadmap.json).', '',
    'Scope: XS = tiny, S = small, M = medium, L = large, XL = very large, XXL = multiple systems. These describe the full target; inspect existing work and upstream reuse to estimate the remaining work.', '']
  for (const [key, category] of Object.entries(categories)) {
    const items = (roadmap.items ?? []).filter((item) => item.category === key)
    if (!items.length) continue
    const showNotes = key === 'parked' || key === 'migration'
    lines.push('### ' + category.label, '', showNotes ? '| Status | Feature | Note |' : '| Status | Feature | Scope |', '|:---:|---|---|')
    for (const item of items) {
      const estimate = String(item.summary ?? '').match(/estimated(?:\s+(?:full|full-system))?\s+scope\s+((?:XXL|XL|XS|S|M|L)(?:\s*[–—-]\s*(?:XXL|XL|XS|S|M|L))?)(?=\W|$)/i)
      const size = item.size ?? estimate?.[1] ?? '—'
      const summary = estimate?.index === 0 ? item.summary.slice(estimate[0].length).replace(/^\.\s*/, '') : item.summary
      const title = String(item.title).replace(/\s*[—–-]\s*Complete$/i, '')
      lines.push('| ' + cell(roadmap.statuses[item.status].icon) + ' | ' + cell(title) + ' | ' + cell(showNotes ? summary : size) + ' |')
    }
    lines.push('')
  }
  return lines.join('\n')
}

async function fetchCategory(category) {
  const titles = []
  let continuation = {}
  do {
    const query = new URLSearchParams({ action: 'query', list: 'categorymembers', cmtitle: category.wikiCategory, cmlimit: '500', cmnamespace: '0', format: 'json', ...continuation })
    const response = await fetch(API + '?' + query, { headers: { 'User-Agent': 'OpenRune-Server content index' } })
    if (!response.ok) throw new Error('Wiki request failed: HTTP ' + response.status)
    const json = await response.json()
    if (json.error || !Array.isArray(json.query?.categorymembers)) throw new Error('Invalid wiki category response: ' + category.wikiCategory)
    titles.push(...json.query.categorymembers.map((member) => member.title))
    continuation = json.continue
  } while (continuation)
  const base = category.wikiCategory.replace('Category:', '')
  const excluded = new Set([base, base.replace(/es$/, ''), base.replace(/s$/, ''), ...(category.exclude ?? [])])
  return [...new Set(titles)].filter((title) => !excluded.has(title)).sort()
}

async function main() {
  const args = new Set(process.argv.slice(2))
  for (const arg of args) if (!['--check', '--fetch-wiki'].includes(arg)) throw new Error('Unknown argument: ' + arg)
  if (args.has('--check') && args.has('--fetch-wiki')) throw new Error('--check cannot be combined with --fetch-wiki; check is offline and read-only')

  const readme = fs.readFileSync(P('README.md'), 'utf8')
  const progress = fs.readFileSync(P('PROGRESS.md'), 'utf8')
  // Validate every destination before fetching or writing anything.
  checkMarkers(readme, README_MARKERS, 'README.md')
  checkMarkers(progress, ROADMAP_MARKERS, 'PROGRESS.md')
  const spec = readJson('tools/progress/features.json')
  const roadmap = renderRoadmap(readJson('tools/progress/roadmap.json'))
  const wiki = readJson(WIKI_FILE)
  if (args.has('--fetch-wiki')) {
    wiki.categories ??= {}
    for (const category of spec.categories.filter((item) => item.wikiCategory)) wiki.categories[category.wikiCategory] = await fetchCategory(category)
    wiki.fetched = new Date().toISOString().slice(0, 10)
  }
  const modules = listModules()
  const engineRoots = new Set(spec.categories.flatMap((category) => (category.features ?? []).map((feature) => feature.engine).filter(Boolean)))
  const engineModules = new Map([...engineRoots].map((root) => [root, listModules(root)]))
  const ctx = { modules, engineModules, references: scanReferences(), npcs: loadNpcs(), dropTables: loadDropTables() }
  const categories = spec.categories.map((category) => {
    const features = category.features ?? (wiki.categories?.[category.wikiCategory] ?? []).map((title) => {
      const module = findModule(title, category.moduleRoot, modules, spec.aliases ?? {})
      return { name: title, wiki: title, module: module?.path }
    })
    return { name: category.name, features: features.map((feature) => inspectFeature(feature, ctx)) }
  })
  const outputs = new Map([
    ['CONTENT_INVENTORY.md', renderInventory(categories, modules)],
    ['PROGRESS.md', replaceBlock(progress, ROADMAP_MARKERS, roadmap)],
    ['README.md', replaceBlock(readme, README_MARKERS, '## Content\n\n[Status and roadmap](PROGRESS.md) · [Technical inventory](CONTENT_INVENTORY.md)')],
  ])
  if (args.has('--fetch-wiki')) outputs.set(WIKI_FILE, JSON.stringify(wiki, null, 2) + '\n')
  const changed = [...outputs].filter(([file, text]) => !fs.existsSync(P(file)) || fs.readFileSync(P(file), 'utf8') !== text)
  if (args.has('--check')) {
    if (changed.length) {
      process.stderr.write('Stale generated output: ' + changed.map(([file]) => file).join(', ') + '\nRun node tools/progress/content-progress.mjs\n')
      process.exitCode = 1
    } else process.stderr.write('Generated output is up to date.\n')
    return
  }
  for (const [file, text] of changed) fs.writeFileSync(P(file), text)
  process.stderr.write(changed.length ? 'Updated: ' + changed.map(([file]) => file).join(', ') + '\n' : 'Generated output is up to date.\n')
}

main().catch((error) => {
  process.stderr.write(error.message + '\n')
  process.exitCode = 1
})
