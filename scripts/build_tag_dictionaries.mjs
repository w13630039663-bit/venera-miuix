#!/usr/bin/env node
/*
 * 重新生成两份标签词典资产（方案见 gallery-tag-category-translation-and-nav-2026-09.md）。
 *
 *   node scripts/build_tag_dictionaries.mjs
 *
 * 产物：
 *   app/src/main/assets/tags.json                 EhTagTranslation 全库（简体，显示层用）
 *   app/src/main/assets/gallery_tags_<行数>.sqlite ffdkj Danbooru 对照表（档位 + 译名，画廊用）
 *   app/src/main/assets/licenses/*                两份数据的许可原文
 *
 * 行数写进文件名是有意的：运行期那份从 assets 复制到 filesDir 后就再也不读 assets
 * （Android 的 SQLite 需要真实路径），**文件名变了才会触发重新复制**。
 * 换了别的刷新信号（同一份数据换个日期）不会让旧副本失效，就会一直读旧表。
 *
 * 源文件缓存在 build/tagdict/ 下，缺了就 curl 取。
 * 本机直连这两个域名都不通，需要代理时给 PROXY_URL（例：PROXY_URL=http://127.0.0.1:7890）。
 *
 * ⚠️ tags_tw.json（繁体）不在这个脚本的范围内：EhTagTranslation 的 Database 仓库只有简体，
 *    繁体那份另有出处，刷新它会让简繁两版的键集合漂开。刷新后简体比繁体全，
 *    繁体模式下多出来的键会按 TagDisplay 的既有口径原样显示 —— 宁可不译不可译错。
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import zlib from 'node:zlib';

const ROOT = path.resolve(import.meta.dirname, '..');
const CACHE = path.join(ROOT, 'build', 'tagdict');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'assets');
const PROXY = process.env.PROXY_URL ? ['-x', process.env.PROXY_URL] : [];

// ffdkj 表按 post_count 截断的这一档：档位越低包体与常驻内存越小。
// 实测（方案文档 §0.7）：>=100 → 79,415 行 / 压缩后 1.44 MB / 语料加权覆盖 66.1%。
const MIN_POST_COUNT = 100;

const EHTAG_NS = [
  'artist', 'character', 'cosplayer', 'female', 'group', 'language',
  'location', 'male', 'mixed', 'other', 'parody', 'reclass', 'rows',
];

fs.mkdirSync(CACHE, { recursive: true });

function fetch(url, dest) {
  if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return dest;
  console.log('  取', url);
  execFileSync('curl', ['-s', '-L', '--fail', '--max-time', '300', ...PROXY, url, '-o', dest],
    { stdio: 'inherit' });
  return dest;
}

/**
 * EhTagTranslation 的 `database/<ns>.md` 是 markdown 表格：`| 原始标签 | 名称 | 描述 | 链接 |`。
 * 只要前两列，且 `原始标签` 为空的行是小节标题（`== 身体 ==`）不是词条。
 *
 * 键里的空格原样保留：EhTag 词条用空格（`touhou project`），而画廊侧的标签用下划线
 * （`touhou_project`），这层差异由 TagTranslationManager 的调用方归一，不在这里改写键 ——
 * 改了键就和线上库对不上，下次刷新会整片漂移。
 */
function parseEhTagTable(file) {
  const out = {};
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    if (!line.startsWith('| ')) continue;
    const cells = line.slice(2).split(' | ');
    if (cells.length < 2) continue;
    const raw = cells[0].trim();
    const label = cells[1].trim();
    if (!raw || raw === '原始标签' || /^-+$/.test(raw)) continue;
    if (!label) continue;
    out[raw] = label;
  }
  return out;
}

function buildEhTagJson() {
  const root = {};
  let entries = 0;
  for (const ns of EHTAG_NS) {
    const file = fetch(
      `https://raw.githubusercontent.com/EhTagTranslation/Database/master/database/${ns}.md`,
      path.join(CACHE, `ehtag_${ns}.md`),
    );
    const table = parseEhTagTable(file);
    // rows 是命名空间中文名、reclass 是分类标签，两者与其它组同构，直接挂上去；
    // 今日解析器就是把它们当普通命名空间读的，形状不能变。
    if (ns === 'rows') {
      root.rows = table;
    } else {
      root[ns] = table;
      if (ns !== 'reclass') entries += Object.keys(table).length;
    }
    console.log(`  ehtag ${ns.padEnd(10)} ${Object.keys(table).length} 条`);
  }
  return { text: JSON.stringify(root), entries };
}

function buildFfdkjSqlite() {
  const src = fetch(
    'https://raw.githubusercontent.com/ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table/main/tag.sqlite',
    path.join(CACHE, 'ffdkj_tag.sqlite'),
  );
  const db = new DatabaseSync(src, { readOnly: true });
  const rows = db.prepare(
    'select name, category, cn_name from tags where post_count >= ? and (cn_name != name or category = 1)',
  ).all(MIN_POST_COUNT);
  db.close();

  const dest = path.join(CACHE, 'gallery_tags.sqlite');
  fs.rmSync(dest, { force: true });
  const out = new DatabaseSync(dest);
  // WITHOUT ROWID：B 树直接按 name 排，单枚精确查就是主键查，省掉一张二级索引。
  // 没有批量精确查（方案 §0.5 实测过两站接口都收不到多枚），所以这里只需要主键这一种形状。
  out.exec('PRAGMA page_size = 1024;');
  out.exec('CREATE TABLE tags (name TEXT PRIMARY KEY, category INTEGER, cn TEXT) WITHOUT ROWID;');
  out.exec('BEGIN;');
  const ins = out.prepare('INSERT INTO tags (name, category, cn) VALUES (?, ?, ?)');
  for (const r of rows) ins.run(r.name, r.category, r.cn_name === r.name ? null : r.cn_name);
  out.exec('COMMIT;');
  out.exec('VACUUM;');
  out.close();
  return { rows: rows.length, file: dest };
}

function copyLicense(url, name) {
  const src = fetch(url, path.join(CACHE, name));
  fs.copyFileSync(src, path.join(ASSETS, 'licenses', name));
}

console.log('== EhTagTranslation → assets/tags.json');
const ehtag = buildEhTagJson();
const tagsJson = path.join(ASSETS, 'tags.json');
const before = fs.statSync(tagsJson).size;
fs.writeFileSync(tagsJson, ehtag.text);
console.log(`  写入 tags.json：${ehtag.entries} 词条，${before} B → ${fs.statSync(tagsJson).size} B`);

console.log('== ffdkj → assets/gallery_tags_<行数>.sqlite');
const ffdkj = buildFfdkjSqlite();
const packed = fs.readFileSync(ffdkj.file);
const assetName = `gallery_tags_${ffdkj.rows}.sqlite`;
for (const stale of fs.readdirSync(ASSETS).filter(f => f.startsWith('gallery_tags_') && f !== assetName)) {
  fs.rmSync(path.join(ASSETS, stale));
  console.log(`  删掉旧的一份 ${stale}（行数变了 = 数据换了一批，运行期那份副本会按新文件名重新复制）`);
}
fs.writeFileSync(path.join(ASSETS, assetName), packed);
console.log(`  写入 ${assetName}：${ffdkj.rows} 行 (post_count>=${MIN_POST_COUNT})，` +
  `盘上 ${(packed.length / 1048576).toFixed(2)} MB，` +
  `APK 里按 deflate 约 ${(zlib.deflateSync(packed).length / 1048576).toFixed(2)} MB`);

console.log('== 许可');
copyLicense('https://raw.githubusercontent.com/EhTagTranslation/Database/master/LICENSE.md',
  'ehtagtranslation-LICENSE.md');
copyLicense('https://raw.githubusercontent.com/ffdkj/ffdkj-Danbooru_Tag-Chinese-English-Translation-Table/main/LICENSE',
  'ffdkj-danbooru-tags-LICENSE.txt');

console.log('完成。记得跑 :app:testDebugUnitTest 与 :app:assembleDebug 复验。');
