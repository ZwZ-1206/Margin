import { IconHeart } from './Icons';

/* 赞助卡：醒目入口。收款码由使用者在设置页/此处配置上传 */
export function SponsorCard({ compact = false }: { compact?: boolean }) {
  return (
    <div className="card sponsor-card">
      <div>
        <div className="qr-frame">
          <img src="sponsor-qr.jpg" alt="微信收款码" className="qr-img" width={168} height={204} />
        </div>
        <p className="qr-hint">微信扫码 · 自愿赞助</p>
      </div>
      <div>
        <h3>
          <IconHeart size={18} /> 喜欢 Margin「留白」？请开发者喝杯咖啡
        </h3>
        <p>
          「留白」<b>完全免费</b>：无会员、无订阅、无广告、无功能墙。
          如果它帮你少看了一会儿手机，可以自愿扫码赞助——这不会解锁任何功能，因为所有功能本来就已经免费。
        </p>
      </div>
      {!compact && (
        <a className="btn btn-accent" href="#/sponsor" onClick={(e) => {
          e.preventDefault();
          window.location.hash = '#/sponsor';
        }}>
          <IconHeart size={18} /> 赞助支持
        </a>
      )}
    </div>
  );
}
