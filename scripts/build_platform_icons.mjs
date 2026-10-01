#!/usr/bin/env node
/*
 * 生成画师介绍页「平台入口」那排胶囊用的**品牌字形**资产（批次 Q+R · 批量 6）。
 *
 *   node scripts/build_platform_icons.mjs
 *
 * 产物（五枚，落 app/src/main/res/drawable/）：
 *   ic_platform_pixiv.xml  ic_platform_x.xml  ic_platform_instagram.xml
 *   ic_platform_tumblr.xml ic_platform_youtube.xml
 *
 * 字形一律填**纯白**（#FFFFFF）：屏上它们的底色是各自的品牌色小方块（见
 * `GalleryPlatformColors`），白字形压在品牌色上，与站标那枚「固定底板 + 定色图形」
 * 是同一条口径 —— 资产本身不带色，色由 token 那一处给，深浅两档与两套主题都不用重出图。
 *
 * ── 为什么源取 Simple Icons（而不是各家官网）──
 *
 * 官网原档形态不齐：pixiv 的 brand 页只给 PNG 与一份带渐变的长图，Instagram 的 logo
 * 是渐变字形（本仓禁渐变，且压成单色会失真），YouTube 只给带红底的图标图。
 * Simple Icons 是这几家的**单色单路径**整理版，正好是这里要的形状，且它的数据文件
 * 逐条记着官方出处与 hex，可核对（本脚本读的就是它那一份 JSON）。
 * 许可证 CC0-1.0（图形本身仍是各家商标，仅用于标明"这条链接是哪一家"）。
 *
 * ── 为什么没有 FANBOX ──
 *
 * 这是**量过之后的结论，不是漏做**（2026-10-01 实测，别再"顺手补一个"）：
 *   - Simple Icons 无 fanbox 条目（`icons/fanbox.svg` 与 `pixivfanbox.svg` 都 404）。
 *   - Iconify 全库搜 `fanbox` 只命中 `arcticons:fanbox-viewer` —— 那是第三方画的安卓应用图标，不是品牌标。
 *   - 站方唯一可用的资产是 `https://s.pximg.net/common/images/fanbox/apple-touch-icon.png`
 *     （180×180）与 `https://www.fanbox.cc/favicon.ico`（16/32/48 三档 BMP）：
 *     两枚都是**同一张彩色吉祥物**（浅黄底 #FAF18A + 白色小兽 + 黑描边）。
 *     它是**彩色方块**，不是单色字形 —— 染色会得到一整块实心方（它的 alpha 处处为 255），
 *     摆在品牌色小方块上也不成立。
 *   ⇒ 代码侧退成**品牌色底 + 字母标「F」**，与站标那条"有真图标就摆真图标、
 *     没有就摆品牌色字母标"的既有口径同一份（见 `VeneraGallerySourceMark` 头注）。
 *     站方哪天放出矢量档，这里加一条 slug 即可，代码侧不用改。
 *
 * ── 校验口径 ──
 *
 * 与 build_source_icons.mjs 逐字同源：**形状不对就直接报错退出，不猜**。
 * 多 path、带 transform、出现 VectorDrawable 不支持的命令，三种都算"上游结构变了"，
 * 必须人工复核 —— 猜着拼出来的结果在屏上"看起来就是个图标"，没人会发现是错的。
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '..');
const CACHE = path.join(ROOT, 'build', 'platformicons');
const DRAWABLE = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable');
const PROXY = process.env.PROXY_URL ? ['-x', process.env.PROXY_URL] : [];

/** 源版本钉死：`@latest` 会让上游改图形时我们**静默**跟着变，而图标变化没人会来报。 */
const SIMPLE_ICONS_VERSION = '15';
const BASE = `https://cdn.jsdelivr.net/npm/simple-icons@${SIMPLE_ICONS_VERSION}/icons`;

/** 白字形。理由见文件头：色由 `GalleryPlatformColors` 那一处给，资产不带色。 */
const GLYPH_WHITE = '#FFFFFFFF';

/** Simple Icons 的 viewBox 一律 0 0 24 24；原样搬进 VectorDrawable 的 viewport。 */
const VIEWPORT = 24;

/**
 * 平台 enum → 上游的文件名与**标题**。**键就是 `GalleryArtistLinkPlatform` 的名字**，
 * 所以加一档平台时这里漏了会在代码侧一眼看出来（那一档没有图标可摆）。
 *
 * 为什么要带 `title`：上游的数据文件里**没有 slug 字段**（只有 title/hex/source/guidelines，
 * 2026-10-01 核过 v15.22.0），而"官方色与出处"要靠它去索引。用文件名倒推 slug 会错
 * （`.ENV` 的文件叫 `dotenv.svg`），所以这里显式写死两个名字。
 */
const ICONS = {
  PIXIV: { slug: 'pixiv', title: 'pixiv' },
  TWITTER: { slug: 'x', title: 'X' },
  INSTAGRAM: { slug: 'instagram', title: 'Instagram' },
  TUMBLR: { slug: 'tumblr', title: 'Tumblr' },
  YOUTUBE: { slug: 'youtube', title: 'YouTube' },
};

fs.mkdirSync(CACHE, { recursive: true });
fs.mkdirSync(DRAWABLE, { recursive: true });

function fetch(url, dest) {
  if (fs.existsSync(dest) && fs.statSync(dest).size > 0) return dest;
  console.log('  取', url);
  execFileSync('curl', ['-s', '-L', '--fail', '--max-time', '120', ...PROXY, url, '-o', dest],
    { stdio: 'inherit' });
  return dest;
}

/**
 * 从 Simple Icons 的 SVG 里抠出那条 path 的 `d`。
 *
 * 只认**单 path、无 transform** 的形状（实测这五枚都是）。多路径的图标（有几家的图标
 * 由两个不连通区域组成，Simple Icons 用一条 d 里的多个 M 表达，仍算单 path）不走分支，
 * 一律报错让人来判 —— 合并路径会丢掉填充规则，拼错的结果是形状不对且看不出来。
 */
function extractPathData(svg) {
  const paths = [...svg.matchAll(/<path\b[^>]*\bd="([^"]+)"[^>]*>/g)];
  if (paths.length !== 1) {
    throw new Error(`期望恰好 1 条 <path>，实得 ${paths.length} 条 —— 上游结构变了，需要人工复核`);
  }
  if (/transform=/.test(svg)) {
    throw new Error('出现 transform= —— VectorDrawable 不支持，需要人工先烘焙进坐标');
  }
  const unsupported = new Set();
  const allowed = new Set('MLHVCSQTAZmlhvcsqtaz'.split(''));
  for (const cmd of paths[0][1].match(/[A-Za-z]/g) ?? []) {
    if (!allowed.has(cmd)) unsupported.add(cmd);
  }
  if (unsupported.size > 0) {
    throw new Error(`路径含 VectorDrawable 不支持的命令：${[...unsupported].join(' ')}`);
  }
  // 连续空白折成一个空格：逗号与空格都是合法分隔符，折行只是为了产物好读。
  return paths[0][1].replace(/\s+/g, ' ').trim();
}

/**
 * 上方 `source` / `hex` 那两行是**核对用**的：Simple Icons 的数据文件逐条记着官方出处与品牌色。
 * 这里读一次并打印，是为了让"这枚图形是从哪拿的、官方色是多少"在构建日志里留痕 ——
 * 色值本身落在 `GalleryPlatformColors`（那里才是屏上认账的那一份）。
 *
 * ⚠️ 数据文件**不在 npm 包里**（`simple-icons@15/_data/simple-icons.json` 是 404，
 * jsdelivr 会回一页 HTML，`curl --fail` 才拦得住）。所以先把 `@15` 解析成精确版本，
 * 再按那个 tag 去 GitHub 取 —— 顺带让产物注释里的出处是可回溯的固定版本号，
 * 而不是一个会漂的 `@15`。
 */
const resolvedVersion = JSON.parse(
  fs.readFileSync(fetch(`https://cdn.jsdelivr.net/npm/simple-icons@${SIMPLE_ICONS_VERSION}/package.json`,
    path.join(CACHE, 'package.json')), 'utf8'),
).version;
const metadataUrl =
  `https://raw.githubusercontent.com/simple-icons/simple-icons/${resolvedVersion}/data/simple-icons.json`;
const metadataJson = JSON.parse(
  fs.readFileSync(fetch(metadataUrl, path.join(CACHE, 'simple-icons.json')), 'utf8'),
);
// 这一份在 npm 包里是 `{ icons: [...] }`、在 GitHub 上是裸数组 —— **两种都认**。
// 只认一种的话拿到的是空表，而空表会把"每一条都查不到"伪装成"上游版本对不上"，
// 报出来的错跟真因差着十万八千里（第一次就是这么绕了一圈）。
const metadata = Array.isArray(metadataJson) ? metadataJson : (metadataJson.icons ?? []);
if (metadata.length === 0) {
  throw new Error(`数据文件解析出 0 条 —— 上游结构变了，需要人工复核（${metadataUrl}）`);
}

const byTitle = new Map(metadata.map((entry) => [entry.title, entry]));

for (const [platform, { slug, title }] of Object.entries(ICONS)) {
  const svg = fs.readFileSync(fetch(`${BASE}/${slug}.svg`, path.join(CACHE, `${slug}.svg`)), 'utf8');
  const pathData = extractPathData(svg);
  const meta = byTitle.get(title);
  if (meta == null) {
    throw new Error(`上游数据文件里没有标题为「${title}」的那一条 —— 版本对不上，需要人工复核`);
  }
  const officialHex = `#${String(meta.hex).toUpperCase()}`;

  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!--
  ${title} 品牌字形（画师介绍页平台入口用）。**不要手改这个文件** —— 它由构建脚本生成：

      node scripts/build_platform_icons.mjs

  源：${BASE}/${slug}.svg（Simple Icons v${resolvedVersion}，CC0-1.0）
  官方出处：${meta.source ?? '（上游未记）'}
  品牌色（官方）：${officialHex} —— 屏上认账的那一份在 \`GalleryPlatformColors\`

  fillColor 是**纯白**：底色由品牌色小方块给（见 GalleryPlatformColors），
  资产本身不带色，深浅两档与两套主题都不用重出图。

  ⚠️ 这是 ${title} 的商标，仅用于标明"这条外链指向哪一家"。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${VIEWPORT}"
    android:viewportHeight="${VIEWPORT}">
    <path
        android:fillColor="${GLYPH_WHITE}"
        android:pathData="${pathData}" />
</vector>
`;

  const target = path.join(DRAWABLE, `ic_platform_${slug}.xml`);
  fs.writeFileSync(target, xml);
  console.log(`  写 ${path.relative(ROOT, target)}（${platform} · pathData ${pathData.length} 字符）`);
}

console.log('完成。FANBOX 无可用矢量档，代码侧退品牌色字母标 —— 理由见本脚本头注。');
