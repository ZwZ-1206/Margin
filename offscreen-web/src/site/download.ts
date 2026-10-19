/** 下载分发数据：厂商安装指南、权限清单、镜像与校验信息的集中定义 */

export interface ReleaseInfo {
  version: string;
  versionCode: number;
  sizeMB: number;
  minAndroid: string;
  targetAndroid: string;
  releaseDate: string;
  notes: string;
  apk: { url: string; sha256: string; sizeBytes: number };
  mirrors: { name: string; url: string; note: string }[];
  certSha256: string;
  virusScan: string;
}

/** 与 public/downloads/version.json 同步的内置回退值（静态部署时接口不可用时展示） */
export const FALLBACK_RELEASE: ReleaseInfo = {
  version: '1.0.0',
  versionCode: 1,
  sizeMB: 8,
  minAndroid: 'Android 9.0',
  targetAndroid: 'Android 16',
  releaseDate: '',
  notes: '首个正式版：专注番茄钟、屏幕使用洞察、限额与挑战、桌面小部件、本地备份。完全免费，无会员，无广告。',
  apk: { url: '', sha256: '', sizeBytes: 0 },
  mirrors: [
    { name: 'GitHub Releases', url: 'https://github.com/ZwZ-1206/Margin/releases/latest', note: '国际线路备用' },
    { name: '网盘备用', url: '', note: '国内线路备用' },
  ],
  certSha256: '',
  virusScan: '',
};

/** 分厂商「安装未知应用」开关路径（2026 主流系统） */
export const VENDOR_GUIDES: { vendor: string; system: string; path: string; tip: string }[] = [
  {
    vendor: '小米 / Redmi',
    system: 'HyperOS / MIUI',
    path: '设置 → 应用设置 → 权限管理 → 安装未知应用 → 选择你的浏览器 → 允许',
    tip: '部分机型在下载完成页直接弹出「允许」入口，点一下即可',
  },
  {
    vendor: '华为 / 荣耀',
    system: 'HarmonyOS / MagicOS',
    path: '设置 → 安全 → 更多安全设置 → 安装未知应用 → 选择你的浏览器 → 允许',
    tip: '华为较新机型需先关闭「纯净模式」或选择「退出纯净模式」后再安装',
  },
  {
    vendor: 'OPPO / 一加',
    system: 'ColorOS',
    path: '设置 → 权限与隐私 → 安装未知应用 → 选择你的浏览器 → 允许',
    tip: 'realme 机型路径相同；若使用 OPPO 浏览器，默认已放行',
  },
  {
    vendor: 'vivo / iQOO',
    system: 'OriginOS / Funtouch',
    path: '设置 → 安全与隐私 → 权限管理 → 安装未知来源应用 → 选择你的浏览器 → 允许',
    tip: 'i管家可能二次拦截，选择「继续安装」即可',
  },
  {
    vendor: '三星',
    system: 'One UI',
    path: '设置 → 生物识别和安全性 → 安装未知应用程序 → 选择你的浏览器 → 允许',
    tip: 'Google Play 保护机制提示属正常现象，选择「仍要安装」',
  },
  {
    vendor: '原生 / 谷歌 Pixel',
    system: 'Android 9–16',
    path: '设置 → 应用与通知 → 特殊权限 → 安装未知应用 → 选择你的浏览器 → 允许',
    tip: 'Chrome 下载后点击通知栏文件即可触发安装',
  },
];

/** 安装通用步骤 */
export const INSTALL_STEPS = [
  '点击「下载 APK」按钮，等待文件下载完成（约 3MB）',
  '下载完成后点击通知栏中的文件；若浏览器拦截，改用文件管理器打开 Download 目录中的 apk',
  '系统弹出「未知来源应用」提示时，按上方对应机型的路径放行你的浏览器',
  '回到安装界面点击「安装」；Google Play 保护机制如弹出风险提示，选择「仍要安装」——本应用不含任何恶意行为',
  '安装完成后打开，按引导授予「使用情况访问」权限，即可开始使用',
];

/** 权限清单：与 APK 实际申请的权限一一对应（详见 docs/03） */
export const PERMISSIONS: { name: string; why: string; sensitive: boolean }[] = [
  { name: '使用情况访问', why: '统计各应用使用时长与打开次数，屏幕洞察与限额的核心数据源', sensitive: true },
  { name: '前台服务', why: '专注计时期间保持计时通知与白噪音播放', sensitive: false },
  { name: '精确闹钟', why: '专注结束提醒与每日报告定时', sensitive: false },
  { name: '开机自启', why: '重启后恢复监控与每日报告', sensitive: false },
  { name: '通知', why: '专注状态、限额预警、每日报告', sensitive: false },
  { name: '悬浮窗', why: '专注进行中的迷你计时浮窗（可在设置关闭）', sensitive: false },
  { name: '震动', why: '专注结束与番茄钟阶段切换的触觉反馈', sensitive: false },
  { name: '录音', why: '仅在你主动开启「专注打卡音」时录制并本地保存', sensitive: true },
  { name: '相机', why: '扫描收款码图片时使用（仅读取相册/拍照，照片不上传）', sensitive: true },
  { name: '存储（照片）', why: '导入导出备份文件、保存分享卡片到相册', sensitive: false },
];

/**
 * 站点正式域名。部署后请改为真实域名——二维码必须编码绝对地址，
 * 相对路径在手机浏览器里无法访问。
 */
export const SITE_ORIGIN = 'https://margin-8ux.pages.dev';

/** 把相对路径拼成绝对地址（已是 http(s) 开头则原样返回） */
export function absoluteUrl(path: string): string {
  if (!path) return '';
  if (/^https?:\/\//.test(path)) return path;
  return SITE_ORIGIN + '/' + path.replace(/^\//, '');
}

/** 运行时获取 version.json；失败时返回 null 由调用方用回退值 */
export async function fetchRelease(): Promise<ReleaseInfo | null> {
  try {
    const res = await fetch(`downloads/version.json?_=${Date.now()}`, { cache: 'no-store' });
    if (!res.ok) return null;
    const data = (await res.json()) as ReleaseInfo;
    if (!data.version) return null;
    return data;
  } catch {
    return null;
  }
}

/** 版本号比较：a 比 b 新返回 true */
export function isNewer(a: string, b: string): boolean {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0);
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const va = pa[i] ?? 0;
    const vb = pb[i] ?? 0;
    if (va !== vb) return va > vb;
  }
  return false;
}

/** 复制到剪贴板，返回是否成功 */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
