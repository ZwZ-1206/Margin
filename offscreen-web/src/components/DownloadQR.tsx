import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';

/**
 * 真实二维码：优先编码 version.json 中的 APK 直链；
 * 未发布时退化为编码当前页面地址（扫码在手机上打开本页）。
 */
export function DownloadQR({ target, size = 168 }: { target: string; size?: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const canvas = canvasRef.current;
    if (!canvas || !target) return;
    QRCode.toCanvas(canvas, target, {
      width: size,
      margin: 1,
      errorCorrectionLevel: 'M',
      color: {
        dark: '#1F2430',
        light: '#FFFFFF',
      },
    }).catch(() => {
      if (!cancelled) setFailed(true);
    });
    return () => {
      cancelled = true;
    };
  }, [target, size]);

  if (failed) {
    return (
      <div className="qr-fallback" style={{ width: size, height: size }}>
        二维码生成失败
      </div>
    );
  }
  return <canvas ref={canvasRef} width={size} height={size} className="qr-canvas" aria-label="下载二维码" />;
}
