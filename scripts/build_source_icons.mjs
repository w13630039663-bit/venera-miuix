#!/usr/bin/env node
/*
 * 生成画廊两站的「来源标识」资产（方案见 gallery-home-round2-2026-09-30 那一份计划）。
 *
 *   node scripts/build_source_icons.mjs
 *   PROXY_URL=http://127.0.0.1:7890 node scripts/build_source_icons.mjs   # 本机直连不通时
 *
 * 产物（两枚，2026-09-30 定型）：
 *   app/src/main/res/drawable/ic_source_gelbooru.xml         ← 由站方 SVG 转出的 VectorDrawable
 *   app/src/main/res/drawable-nodpi/ic_source_yandere.png    ← 由站方 favicon.ico 解出的 16×16 RGBA
 *
 * yande.re 那一枚是**用户 2026-09-30 知情后拍板**要的，把当时否掉它的理由留在原地，别再猜一遍：
 *   - 站方**没有方形站标**：favicon.png / apple-touch-icon* / logo.png / static/ / img/ 全部 404，
 *     `assets.yande.re` 上唯一的大档是 484×75 的**横版字标**（含三个角色插画，裁方要靠眼估）。
 *   - 第三方也没有更高清的档：Google `s2/favicons?sz=128` 两站都只回 **16×16 原图**（它不造分辨率）、
 *     unavatar 的 `website/` 404、Brandfetch 回它自己的网页。
 *   - 那 16×16 实测**256 个像素全不透明**，主色 #fff3e4(12) / #522726(6) / #333333(3) / #ffbabb(2)
 *     —— 是一张**裁自插画的粉脸**，不是设计出来的站标，摆到 24dp（本机 ≈72 物理像素）必然糊。
 *     用户仍选它：这张脸就是 yande.re 在浏览器标签页上的标识，与站方一致优先于"好看"。
 *   - 产物落 `drawable-nodpi`：16 像素就是 16 像素，不许系统再按屏幕密度预缩一遍。
 *
 * 为什么只有 Gelbooru 走矢量（2026-09-30 实测，别再"顺手补一个"）：
 *   - Gelbooru 的 favicon.ico（名 .ico 实为 PNG）与 .png 都只有 **16×16**，放大到 24~28dp 会糊；
 *     但站上另有一枚 `layout/gelbooru-logo.svg`（360×360 单路径），**可直接转 VectorDrawable**。
 *     本脚本取的就是它 —— 矢量在任意密度下都清晰，且不需要任何光栅化工具
 *     （本机没有 magick / ffmpeg / python）。
 *
 * 许可与归属：这两枚都是**站方商标**，仅用于标明"这张图来自哪一站"。
 * 仓里此前有一条"不引站方 logo 资产"的旧口径（见 GallerySearchSource 的注释），
 * 2026-09-30 用户明确要求用原图库图标，该口径由本轮推翻并已就地改写指向本资产。
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const ROOT = path.resolve(import.meta.dirname, '..');
const CACHE = path.join(ROOT, 'build', 'sourceicons');
const DRAWABLE = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable');
const DRAWABLE_NODPI = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const PROXY = process.env.PROXY_URL ? ['-x', process.env.PROXY_URL] : [];

const GELBOORU_SVG = 'https://gelbooru.com/layout/gelbooru-logo.svg';
const YANDERE_ICO = 'https://yande.re/favicon.ico';

/**
 * 品牌蓝，取自站方 favicon 本身（不透明像素里除白色外出现最多的就是它）。
 *
 * ⚠️ 站方那枚 SVG 的 path `fill` 是 **#FFFFFF** —— 它是设计给**彩色底**用的
 * （logo 摆在站方自己的蓝色页头上）。直接照抄白色，摆到我们那张固定深色小底板上
 * 就成了一块没有识别度的白斑。所以这里按**品牌蓝**上色。
 * 与固定深色底板的对比度 ≈ 3.7:1，高于图形元素 3:1 的门槛（非文本，不适用 4.5:1）。
 */
const GELBOORU_BLUE = '#006FFA';

/** 站方 SVG 的 viewBox 是 0 0 360 360；原样搬进 VectorDrawable 的 viewport。 */
const VIEWPORT = 360;

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
 * 从 SVGO 里抠出那条 path 的 `d`。
 *
 * 只认**单 path、无 transform** 的形状 —— 站方这枚正好是（实测：1 个 `<path>`、
 * 命令集 `C H L M S V c h l s v z` 全在 VectorDrawable 的支持表内、无渐变/无 `<use>`）。
 * 一旦站方改版成多路径或加了 transform，这里**直接报错退出**而不是猜着拼：
 * 拼错的结果是一枚形状不对的图标，而它在屏上看起来"就是个图标"，没人会发现。
 */
function extractPathData(svg) {
  const paths = [...svg.matchAll(/<path\b[^>]*\bd="([^"]+)"[^>]*>/g)];
  if (paths.length !== 1) {
    throw new Error(`站方 SVG 期望恰好 1 条 <path>，实得 ${paths.length} 条 —— 结构变了，需要人工复核`);
  }
  if (/transform=/.test(svg)) {
    throw new Error('站方 SVG 出现 transform= —— VectorDrawable 不支持，需要人工先烘焙进坐标');
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

const svg = fs.readFileSync(fetch(GELBOORU_SVG, path.join(CACHE, 'gelbooru-logo.svg')), 'utf8');
const pathData = extractPathData(svg);

const xml = `<?xml version="1.0" encoding="utf-8"?>
<!--
  Gelbooru 站标（来源标识用）。**不要手改这个文件** —— 它由构建脚本生成：

      node scripts/build_source_icons.mjs

  源：${GELBOORU_SVG}
  取法：站方 SVG 的单条 <path> 原样搬进 viewport，fill 由它的 #FFFFFF 改成品牌蓝
  ${GELBOORU_BLUE}（原值是为彩色页头设计的，摆在我们的深色小底板上读不出来）。

  ⚠️ 这是 Gelbooru 的商标，仅用于标明条目来自哪一站。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="${VIEWPORT}"
    android:viewportHeight="${VIEWPORT}">
    <path
        android:fillColor="${GELBOORU_BLUE}"
        android:pathData="${pathData}" />
</vector>
`;

const target = path.join(DRAWABLE, 'ic_source_gelbooru.xml');
fs.writeFileSync(target, xml);
console.log(`  写 ${path.relative(ROOT, target)}（pathData ${pathData.length} 字符）`);

/*
 * ── yande.re：favicon.ico → 16×16 RGBA PNG ──────────────────────────────────────────
 *
 * 为什么要脚本产出而不是手塞一张 PNG：Android 的 BitmapFactory 与 Coil 都**解不开 ICO**，
 * 所以必须在构建期转一次；转的过程要可重跑、可核对来源，不能靠"某次谁手动放了一个二进制"。
 *
 * 判据与 Gelbooru 那侧同一条口径：**形状不对就直接报错退出**，不猜。
 * 站方哪天换成真 PNG 或改了色深，这里会红着告诉我们要复核，而不是产出一枚花掉的图标。
 */
function crc32(buf) {
  if (!crc32.table) {
    crc32.table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let x = n;
      for (let k = 0; k < 8; k++) x = x & 1 ? 0xedb88320 ^ (x >>> 1) : x >>> 1;
      crc32.table[n] = x;
    }
  }
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = (c >>> 8) ^ crc32.table[(c ^ buf[i]) & 0xff];
  return (c ^ -1) >>> 0;
}

function pngChunk(type, data) {
  const head = Buffer.alloc(4);
  head.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([head, body, crc]);
}

function encodePng(width, height, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;   // bit depth
  ihdr[9] = 6;   // color type: RGBA
  const stride = width * 4 + 1;
  const raw = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    raw[y * stride] = 0;   // filter: none（不压缩预测，产物小到 1KB 级）
    rgba.copy(raw, y * stride + 1, y * width * 4, (y + 1) * width * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw)),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

function icoToPng(ico) {
  if (ico.readUInt16LE(0) !== 0 || ico.readUInt16LE(2) !== 1) {
    throw new Error('favicon 不是 ICO 头 —— 站方改了格式，需要人工复核');
  }
  const entries = ico.readUInt16LE(4);
  if (entries !== 1) {
    throw new Error(`期望恰好 1 条目，实得 ${entries} —— 站方给了多档图标，需要人工挑一档`);
  }
  const width = ico[6] || 256;
  const height = ico[7] || 256;
  const offset = ico.readUInt32LE(18);
  // 现代 .ico 常直接内嵌 PNG：那种情况原样搬出去就行。
  if (ico.subarray(offset, offset + 8).toString('hex') === '89504e470d0a1a0a') {
    return ico.subarray(offset);
  }
  const bmp = ico.subarray(offset);
  const bitCount = bmp.readUInt16LE(14);
  if (bitCount !== 8) {
    throw new Error(`色深 ${bitCount}bpp 不在预期内（实测是 8bpp 调色板）—— 需要人工复核`);
  }
  const paletteSize = bmp.readUInt32LE(32) || 256;
  const palette = [];
  for (let i = 0; i < paletteSize; i++) {
    const o = 40 + i * 4;
    palette.push([bmp[o + 2], bmp[o + 1], bmp[o]]);   // ICO 调色板是 BGRA
  }
  const pixelOffset = 40 + paletteSize * 4;
  const rowBytes = Math.ceil((width * bitCount) / 32) * 4;
  // AND 掩码紧跟在 XOR 位图之后，1bpp、每行 4 字节对齐；位=1 表示该像素透明。
  // yande.re 那枚掩码全 0（256 像素全不透明，实测），safebooru 这枚**有**透明像素
  // （16×16 里 72 个）—— 不认掩码的话透明区会被调色板第 0 项染成黑块。
  const maskOffset = pixelOffset + rowBytes * height;
  const maskRowBytes = Math.ceil(width / 32) * 4;
  const rgba = Buffer.alloc(width * height * 4);
  for (let y = 0; y < height; y++) {
    const srcRow = height - 1 - y;   // BMP 自底向上
    const maskRow = ico.subarray(offset + maskOffset + y * maskRowBytes, offset + maskOffset + (y + 1) * maskRowBytes);
    for (let x = 0; x < width; x++) {
      const index = bmp[pixelOffset + srcRow * rowBytes + x];
      const [r, g, b] = palette[index] ?? [0, 0, 0];
      const o = (y * width + x) * 4;
      rgba[o] = r;
      rgba[o + 1] = g;
      rgba[o + 2] = b;
      const transparent = ((maskRow[x >> 3] >> (7 - (x & 7))) & 1) === 1;
      rgba[o + 3] = transparent ? 0 : 255;
    }
  }
  return encodePng(width, height, rgba);
}

const ico = fs.readFileSync(fetch(YANDERE_ICO, path.join(CACHE, 'yandere-favicon.ico')));
const png = icoToPng(ico);
fs.mkdirSync(DRAWABLE_NODPI, { recursive: true });
const yandereTarget = path.join(DRAWABLE_NODPI, 'ic_source_yandere.png');
fs.writeFileSync(yandereTarget, png);
console.log(`  写 ${path.relative(ROOT, yandereTarget)}（${png.length} B）`);

/*
 * ── Safebooru：favicon.ico → 16×16 RGBA PNG ──────────────────────────────────────────
 *
 * 与 yande.re 同一路数（单条目 ICO、8bpp 调色板、BMP 位图），实测（2026-10-01）：
 * 恰好 1 条目 16×16。**与 yande.re 的关键差别**：这枚**有透明像素**（AND 掩码里
 * 72/256 位置 1，黑底之外是 Danbooru 风格的灰棕笔触）—— 上面 icoToPng 已统一改成
 * 尊重掩码；yande.re 掩码全 0，产物逐字节不变。
 * 产物同样落 `drawable-nodpi`：16 像素就是 16 像素，不许系统按密度预缩。
 * 组件侧（VeneraGallerySourceMark）给它铺白色底板 —— 与 Gelbooru 同一条理由：
 * 站方这枚图形是为浅色浏览器标签栏设计的。
 */
const SAFEBOORU_ICO = 'https://safebooru.donmai.us/favicon.ico';
const safebooruIco = fs.readFileSync(fetch(SAFEBOORU_ICO, path.join(CACHE, 'safebooru-favicon.ico')));
const safebooruPng = icoToPng(safebooruIco);
const safebooruTarget = path.join(DRAWABLE_NODPI, 'ic_source_safebooru.png');
fs.writeFileSync(safebooruTarget, safebooruPng);
console.log(`  写 ${path.relative(ROOT, safebooruTarget)}（${safebooruPng.length} B）`);
console.log('完成。');
