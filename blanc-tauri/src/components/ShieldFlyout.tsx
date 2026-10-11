import React, { useEffect, useRef } from 'react';
import { Tab, AdblockStats } from '../types/browser';
import {
  ShieldCheck,
  Lock,
  Unlock,
  Sparkles,
  Settings,
  Activity,
  CheckCircle2,
  X,
} from 'lucide-react';

interface ShieldFlyoutProps {
  isOpen?: boolean;
  onClose: () => void;
  activeTab: Tab | null;
  adblockStats?: AdblockStats;
  onOpenSettings?: () => void;
  onOpenDiagnostics?: () => void;
}

export const ShieldFlyout: React.FC<ShieldFlyoutProps> = ({
  isOpen = true,
  onClose,
  activeTab,
  adblockStats,
  onOpenSettings,
  onOpenDiagnostics,
}) => {
  const flyoutRef = useRef<HTMLDivElement>(null);
  const [siteProtectionEnabled, setSiteProtectionEnabled] = React.useState(true);

  // Close on outside click or Escape
  useEffect(() => {
    if (isOpen === false) return;

    const handlePointerDown = (e: MouseEvent) => {
      if (flyoutRef.current && !flyoutRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (isOpen === false) return null;

  const url = activeTab?.url || '';
  const isInternal = url.startsWith('blanc://') || url === 'about:blank' || !url;
  const isHttps = url.startsWith('https://');
  const isHttp = url.startsWith('http://') && !isHttps;

  let hostname = 'Blanc Local Surface';
  if (!isInternal) {
    try {
      hostname = new URL(url).hostname.replace(/^www\./, '');
    } catch {
      hostname = url.replace(/^https?:\/\//, '').split('/')[0] || 'Web Page';
    }
  }

  const blockedOnPage = activeTab?.blocked_trackers || 0;

  return (
    <div
      ref={flyoutRef}
      role="dialog"
      aria-label="Security & Privacy Shields"
      className="absolute top-12 right-0 w-[330px] max-w-[calc(100vw-24px)] rounded-2xl bg-[var(--surface-raised,#1c1814)] border border-[var(--border,#3d3226)] shadow-2xl backdrop-blur-2xl p-4 text-[var(--text,#f5f5f5)] z-[100] animate-in fade-in zoom-in-95 duration-150 select-none font-ui pointer-events-auto"
      style={{
        boxShadow: '0 20px 48px -10px rgba(0,0,0,0.7), 0 0 0 1px rgba(255,255,255,0.08)',
      }}
    >
      {/* 1. HEADER: Site Identity & Close Button */}
      <div className="flex items-start justify-between pb-3 border-b border-white/10">
        <div className="flex items-center space-x-2.5 min-w-0 pr-2">
          <div className="w-8 h-8 rounded-xl bg-[#d4ad66]/15 border border-[#d4ad66]/30 flex items-center justify-center text-[#d4ad66] shrink-0">
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div className="min-w-0">
            <h3 className="text-[13.5px] font-semibold text-white/95 truncate tracking-tight">
              {hostname}
            </h3>
            <div className="flex items-center space-x-1.5 mt-0.5">
              {isInternal ? (
                <span className="flex items-center text-[10.5px] text-[#d4ad66] font-medium">
                  <Sparkles className="w-3 h-3 mr-1" /> Blanc Protected Surface
                </span>
              ) : isHttps ? (
                <span className="flex items-center text-[10.5px] text-emerald-400 font-medium">
                  <Lock className="w-3 h-3 mr-1" /> Secure Connection (HTTPS)
                </span>
              ) : isHttp ? (
                <span className="flex items-center text-[10.5px] text-amber-400 font-medium">
                  <Unlock className="w-3 h-3 mr-1" /> Insecure HTTP Connection
                </span>
              ) : (
                <span className="text-[10.5px] text-white/50">Local Protocol</span>
              )}
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="p-1 rounded-lg text-white/40 hover:text-white/80 hover:bg-white/10 transition-colors"
          title="Close shields flyout"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* 2. SHIELD TOGGLE BANNER */}
      {!isInternal && (
        <div className="my-3 p-3 rounded-xl bg-white/[0.03] border border-white/10 flex items-center justify-between">
          <div className="pr-2">
            <div className="text-[12.5px] font-medium text-white/90">
              {siteProtectionEnabled ? 'Shields are UP' : 'Shields Paused'}
            </div>
            <div className="text-[11px] text-white/50 mt-0.5">
              {siteProtectionEnabled
                ? 'Blanc Blocker is filtering ads & trackers'
                : 'Protections temporarily disabled for this site'}
            </div>
          </div>

          <button
            type="button"
            onClick={() => setSiteProtectionEnabled(!siteProtectionEnabled)}
            className={`w-11 h-6 rounded-full transition-colors p-0.5 relative shrink-0 cursor-pointer ${
              siteProtectionEnabled ? 'bg-[#d4ad66]' : 'bg-white/20'
            }`}
            title="Toggle shields for this site"
          >
            <div
              className={`w-5 h-5 rounded-full bg-black shadow-md transform transition-transform ${
                siteProtectionEnabled ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      )}

      {/* 3. TRACKER & AD BLOCK METRICS */}
      <div className="my-3 space-y-2">
        <div className="text-[11px] uppercase tracking-wider font-semibold text-white/40 px-0.5">
          Blocked on this page
        </div>

        <div className="p-3 rounded-xl bg-[#d4ad66]/10 border border-[#d4ad66]/25 flex items-center justify-between">
          <div>
            <div className="text-[22px] font-semibold text-[#d4ad66] font-mono leading-none">
              {blockedOnPage}
            </div>
            <div className="text-[11px] text-[#d4ad66]/80 font-medium mt-1">
              {blockedOnPage === 1 ? 'Ad & tracker prevented' : 'Ads & trackers prevented'}
            </div>
          </div>
          <div className="w-9 h-9 rounded-full bg-[#d4ad66]/20 flex items-center justify-center text-[#d4ad66]">
            <CheckCircle2 className="w-5 h-5" />
          </div>
        </div>

        {/* Global Statistics Grid */}
        <div className="grid grid-cols-2 gap-2 pt-1">
          <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/10">
            <div className="text-[10px] text-white/50">Trackers Detected</div>
            <div className="text-[14px] font-semibold text-white/90 font-mono mt-0.5">
              {(adblockStats?.trackersDetected || 0).toLocaleString()}
            </div>
          </div>
          <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/10">
            <div className="text-[10px] text-white/50">Total Ads Blocked</div>
            <div className="text-[14px] font-semibold text-white/90 font-mono mt-0.5">
              {(adblockStats?.totalBlocked || 0).toLocaleString()}
            </div>
          </div>
        </div>
      </div>

      {/* 4. FOOTER QUICK ACTIONS */}
      <div className="pt-2 border-t border-white/10 flex items-center justify-between text-[11.5px]">
        <button
          type="button"
          onClick={() => {
            onClose();
            onOpenSettings?.();
          }}
          className="flex items-center space-x-1.5 text-white/60 hover:text-white transition-colors py-1 px-1.5 rounded-lg hover:bg-white/5"
        >
          <Settings className="w-3.5 h-3.5" />
          <span>Shield Settings</span>
        </button>

        <button
          type="button"
          onClick={() => {
            onClose();
            onOpenDiagnostics?.();
          }}
          className="flex items-center space-x-1 text-[#d4ad66]/80 hover:text-[#d4ad66] transition-colors py-1 px-1.5 rounded-lg hover:bg-white/5"
        >
          <Activity className="w-3.5 h-3.5" />
          <span>Diagnostics</span>
        </button>
      </div>
    </div>
  );
};
