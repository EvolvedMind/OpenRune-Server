import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const source = path.join(path.dirname(fileURLToPath(import.meta.url)), 'content-progress.mjs')

function fixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'openrune-progress-'))
  t.after(() => fs.rmSync(root, { recursive: true, force: true }))
  const write = (name, value) => {
    fs.mkdirSync(path.dirname(path.join(root, name)), { recursive: true })
    fs.writeFileSync(path.join(root, name), typeof value === 'string' ? value : JSON.stringify(value, null, 2) + '\n')
  }
  write('tools/progress/content-progress.mjs', fs.readFileSync(source, 'utf8'))
  write('README.md', '# Manual README\r\n\r\n<!-- content-progress:start -->\r\nold\r\n<!-- content-progress:end -->\r\n\r\nManual README footer\r\n')
  write('PROGRESS.md', '# Manual status\r\n\r\nAccepted work stays here.\r\n\r\n<!-- roadmap:start -->\r\nold\r\n<!-- roadmap:end -->\r\n\r\nManual progress footer\r\n')
  write('CONTENT_INVENTORY.md', 'Old inventory\n')
  write('tools/progress/features.json', {
    aliases: { 'Actual Boss': 'actual', 'Stub Boss': 'stub' },
    categories: [
      { name: 'Skills', features: [{ name: 'Attack', wiki: 'Attack', engine: 'api/combat' }] },
      { name: 'Bosses', wikiCategory: 'Category:Bosses', moduleRoot: 'content/bosses' },
    ],
  })
  write('tools/progress/wiki-cache.json', {
    categories: { 'Category:Bosses': ['Actual Boss', 'Stub Boss', 'Vanguard', 'Planned Boss', 'Unknown Boss'] },
    pages: { 'Actual Boss': { image: 'https://example.invalid/icon.png', sections: ['Unverified checklist item'] } },
  })
  write('tools/progress/roadmap.json', {
    statuses: { not_added: { icon: '🔴', label: 'Not started' }, started: { icon: '🟡', label: 'Started' } },
    categories: { bosses: { label: 'Bosses' }, parked: { label: 'Geparkeerd' } },
    items: [
      { title: 'Planned Boss — Complete', status: 'not_added', category: 'bosses', summary: 'Estimated scope S–M. Full encounter | rewards.', details: ['Long implementation detail stays in JSON.'] },
      { title: 'Paused system', status: 'started', category: 'parked', size: 'XL', summary: 'Preserve the checkpoint.', details: [] },
    ],
  })
  write('api/combat/formulas/build.gradle.kts', '// fixture\n')
  write('api/combat/formulas/src/main/kotlin/Combat.kt', 'class Combat\n')
  write('content/bosses/actual/build.gradle.kts', '// fixture\n')
  write('content/bosses/actual/src/main/kotlin/Boss.kt', 'class ActualBoss : PluginScript() {}\n')
  write('content/bosses/stub/build.gradle.kts', '// fixture\n')
  write('content/other/pets/build.gradle.kts', '// fixture\n')
  write('content/other/pets/src/main/kotlin/Pets.kt', 'class Pets : PluginScript() { val follower = npc.vanguard }\n')
  write('content/drops/src/main/resources/drops/tables/plannedboss.toml', 'npc = "planned_boss"\n')
  write('osrs-dumps/dump.npc', '[vanguard]\nname=Vanguard\n')
  const read = (name) => fs.readFileSync(path.join(root, name), 'utf8')
  const run = (...args) => spawnSync(process.execPath, ['tools/progress/content-progress.mjs', ...args], { cwd: root, encoding: 'utf8' })
  const snapshot = () => fs.readdirSync(root, { recursive: true }).sort().filter((name) => fs.statSync(path.join(root, name)).isFile())
    .map((name) => [name, fs.readFileSync(path.join(root, name)).toString('base64')])
  return { write, read, run, snapshot }
}

test('generation keeps manual text and distinguishes modules, references, drops and missing code', (t) => {
  const f = fixture(t)
  const result = f.run()
  assert.equal(result.status, 0, result.stderr)
  const progress = f.read('PROGRESS.md')
  assert.ok(progress.startsWith('# Manual status\r\n\r\nAccepted work stays here.\r\n\r\n'))
  assert.ok(progress.endsWith('\r\n\r\nManual progress footer\r\n'))
  assert.equal(progress.replace(/\r\n/g, '').includes('\n'), false)
  assert.match(progress, /\| 🔴 \| Planned Boss \| S–M \|/)
  assert.match(progress, /\| 🟡 \| Paused system \| Preserve the checkpoint\. \|/)
  assert.doesNotMatch(progress, /Full encounter|Gepland|Geparkeerd \|/)
  assert.doesNotMatch(progress, /Long implementation detail/)
  const readme = f.read('README.md')
  assert.ok(readme.startsWith('# Manual README\r\n\r\n'))
  assert.ok(readme.endsWith('\r\n\r\nManual README footer\r\n'))
  const inventory = f.read('CONTENT_INVENTORY.md')
  assert.match(inventory, /\[Actual Boss\].*\| Module \| \[content\/bosses\/actual\]/)
  assert.match(inventory, /\[Stub Boss\].*\| Module stub \|/)
  assert.match(inventory, /\[Vanguard\].*\| References only \|.*Pets\.kt/)
  assert.match(inventory, /\[Planned Boss\].*\| Drop table only \|/)
  assert.match(inventory, /\[Unknown Boss\].*\| Not detected \|/)
  assert.match(inventory, /\[Attack\].*\| Module \| \[api\/combat\]/)
  assert.doesNotMatch(inventory, /<img|Unverified checklist|Estimated scope|Last touched|\| Tests \|/)
  const before = f.snapshot()
  assert.equal(f.run().status, 0)
  assert.deepEqual(f.snapshot(), before, 'second generation must be stable')
  assert.equal(f.run('--check').status, 0)
  assert.deepEqual(f.snapshot(), before, 'clean check must not write')
})

test('--check catches each stale output and never writes', async (t) => {
  for (const name of ['CONTENT_INVENTORY.md', 'PROGRESS.md', 'README.md']) {
    await t.test(name, (t) => {
      const f = fixture(t)
      assert.equal(f.run().status, 0)
      const text = f.read(name)
      f.write(name, name === 'CONTENT_INVENTORY.md' ? 'stale\n' : text.replace(/<!-- (roadmap|content-progress):start -->/, '$&\r\nstale'))
      const before = f.snapshot()
      const result = f.run('--check')
      assert.equal(result.status, 1)
      assert.ok(result.stderr.includes(name))
      assert.deepEqual(f.snapshot(), before)
    })
  }
})

test('invalid destination markers reject the run before any writes', async (t) => {
  for (const file of ['README.md', 'PROGRESS.md']) {
    for (const kind of ['missing', 'duplicate', 'reversed']) {
      await t.test(file + ': ' + kind, (t) => {
        const f = fixture(t)
        const stem = file === 'README.md' ? 'content-progress' : 'roadmap'
        const start = '<!-- ' + stem + ':start -->'
        const end = '<!-- ' + stem + ':end -->'
        let text = f.read(file)
        if (kind === 'missing') text = text.replace(end, '')
        if (kind === 'duplicate') text += '\n' + start + '\n' + end + '\n'
        if (kind === 'reversed') text = end + '\n' + start
        f.write(file, text)
        const before = f.snapshot()
        const result = f.run()
        assert.equal(result.status, 1)
        assert.ok(result.stderr.includes(file))
        assert.deepEqual(f.snapshot(), before)
      })
    }
  }
})

test('--check --fetch-wiki is rejected before fetching or writing', (t) => {
  const f = fixture(t)
  const before = f.snapshot()
  const result = f.run('--check', '--fetch-wiki')
  assert.equal(result.status, 1)
  assert.match(result.stderr, /cannot be combined/)
  assert.deepEqual(f.snapshot(), before)
})

test('roadmap duplicates and unknown categories fail instead of dropping items', async (t) => {
  for (const kind of ['duplicate', 'unknown category']) {
    await t.test(kind, (t) => {
      const f = fixture(t)
      const roadmap = JSON.parse(f.read('tools/progress/roadmap.json'))
      if (kind === 'duplicate') roadmap.items.push({ ...roadmap.items[0], title: 'Planned Boss' })
      else roadmap.items[0].category = 'typo'
      f.write('tools/progress/roadmap.json', roadmap)
      const before = f.snapshot()
      const result = f.run()
      assert.equal(result.status, 1)
      assert.deepEqual(f.snapshot(), before)
    })
  }
})
