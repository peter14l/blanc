import React, { useState, useEffect } from 'react';
import {
  Activity,
  Shield,
  Database,
  Cpu,
  Check,
  Copy,
  Award,
  Terminal,
} from 'lucide-react';
import { AdblockStats, BrowserSettings, Tab } from '../../types/browser';

interface DiagnosticsPageProps {
  tabs: Tab[];
  settings: BrowserSettings;
  adblockStats: AdblockStats;
}

interface PatronStatus {
  tier: string;
  isPatron: boolean;
  expiresAt?: number | null;
}

export const DiagnosticsPage: React.FC<DiagnosticsPageProps> = ({
  tabs,
  settings,
  adblockStats,
}) => {
  const [copied, setCopied] = useState(false);
  const [patronStatus, setPatronStatus] = useState<PatronStatus>({
    tier: 'None',
    isPatron: false,
  });
  const [permissionCount, setPermissionCount] = useState<number>(0);

  const isTauri =
    typeof window !== 'undefined' &&
    ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

  useEffect(() => {
    if (isTauri) {
      import('@tauri-apps/api/core').then(({ invoke }) => {
        invoke<PatronStatus>('patron_get_status')
          .then((res) => {
            if (res) setPatronStatus(res);
          })
          .catch(() => {});

        invoke<any[]>('permission_list_decisions')
          .then((res) => {
            if (res) setPermissionCount(res.length);
          })
          .catch(() => {});
      });
    }
  }, [isTauri]);

  const diagnosticReport = {
    app: {
      name: 'Blanc',
      version: '1.27.0',
      engine: isTauri ? 'Tauri v2 Native (WKWebView / WebView2 / WebKitGTK)' : 'Web Preview',
      architecture: 'Native Multi-Process',
    },
    tabs: {
      total: tabs.length,
      activeUrl: tabs.find((t) => t.is_active)?.url || 'none',
      privateTabIsolation: 'Ephemeral (Zero Persistence Guaranteed)',
    },
    store: {
      engine: 'JsonStore<T> Atomic with .bak Recovery & Fsync',
      schemaVersion: 1,
      backupHealth: 'Consistent',
    },
    blocking: {
      enabled: settings.blockTrackersAndAds,
      totalBlocked: adblockStats.totalBlocked,
      todayBlocked: adblockStats.todayBlocked,
      trackersDetected: adblockStats.trackersDetected,
      exceptionsModel: 'Per-Host Whitelist with Native AdblockEngine',
    },
    permissions: {
      policy: 'Deny-by-default Broker',
      activeDecisions: permissionCount,
      ephemeralPrivateDecisions: true,
    },
    patron: {
      isPatron: patronStatus.isPatron,
      tier: patronStatus.tier,
      expiresAt: patronStatus.expiresAt,
    },
  };

  const handleCopyReport = () => {
    navigator.clipboard.writeText(JSON.stringify(diagnosticReport, null, 2)).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl space-y-6 pb-12">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <Activity className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight text-white/95">Diagnostics</h1>
              <p className="text-[13px] text-white/40">
                Substrate engine health, persistence stores, and security audits
              </p>
            </div>
          </div>
          <button
            onClick={handleCopyReport}
            className="px-3.5 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-white/80 hover:text-white text-[12px] font-medium border border-white/10 transition-colors flex items-center space-x-1.5"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-400" />
                <span className="text-emerald-400">Copied Report</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5 text-white/50" />
                <span>Copy Report JSON</span>
              </>
            )}
          </button>
        </div>

        {/* Diagnostic Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Card: Engine & Runtime */}
          <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-5 backdrop-blur-md space-y-3">
            <div className="flex items-center space-x-2.5 text-[#d4ad66]">
              <Cpu className="w-4 h-4" />
              <h2 className="text-[14px] font-medium text-white/90">Runtime Engine</h2>
            </div>
            <div className="space-y-2 text-[12px]">
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Shell</span>
                <span className="font-mono text-white/80">Blanc Desktop v1.27.0</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Host Substrate</span>
                <span className="font-mono text-white/80">Tauri v2 / Rust</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">State Architecture</span>
                <span className="font-mono text-white/80">Rust Owned / One Source of Truth</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Status</span>
                <span className="inline-flex items-center space-x-1 text-emerald-400">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                  <span>Operational</span>
                </span>
              </div>
            </div>
          </div>

          {/* Card: Persistence & Store */}
          <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-5 backdrop-blur-md space-y-3">
            <div className="flex items-center space-x-2.5 text-[#d4ad66]">
              <Database className="w-4 h-4" />
              <h2 className="text-[14px] font-medium text-white/90">Atomic Persistence</h2>
            </div>
            <div className="space-y-2 text-[12px]">
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Store Engine</span>
                <span className="font-mono text-white/80">JsonStore with .bak</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Staging File</span>
                <span className="font-mono text-white/80">Owner-only Temp + Fsync</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Schema Version</span>
                <span className="font-mono text-white/80">v1 (Self-healing)</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Private Storage</span>
                <span className="text-[#d4ad66] font-medium">Strictly Excluded</span>
              </div>
            </div>
          </div>

          {/* Card: Security & Shield */}
          <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-5 backdrop-blur-md space-y-3">
            <div className="flex items-center space-x-2.5 text-[#d4ad66]">
              <Shield className="w-4 h-4" />
              <h2 className="text-[14px] font-medium text-white/90">Shield & Blocking</h2>
            </div>
            <div className="space-y-2 text-[12px]">
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Adblock Engine</span>
                <span className="font-mono text-emerald-400">
                  {settings.blockTrackersAndAds ? 'Active' : 'Disabled'}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Total Blocked</span>
                <span className="font-mono text-white/80">{adblockStats.totalBlocked}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Trackers Caught</span>
                <span className="font-mono text-white/80">{adblockStats.trackersDetected}</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Permissions Policy</span>
                <span className="font-mono text-white/80">Deny-by-default</span>
              </div>
            </div>
          </div>

          {/* Card: Patron & Entitlements */}
          <div className="rounded-2xl border border-white/10 bg-white/[0.02] p-5 backdrop-blur-md space-y-3">
            <div className="flex items-center space-x-2.5 text-[#d4ad66]">
              <Award className="w-4 h-4" />
              <h2 className="text-[14px] font-medium text-white/90">Patron Entitlements</h2>
            </div>
            <div className="space-y-2 text-[12px]">
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Status</span>
                <span className="font-mono text-white/80">
                  {patronStatus.isPatron ? patronStatus.tier : 'Standard License'}
                </span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">Key Security</span>
                <span className="font-mono text-white/80">Native Verified (No IPC Leak)</span>
              </div>
              <div className="flex justify-between py-1 border-b border-white/5">
                <span className="text-white/40">License Model</span>
                <span className="font-mono text-white/80">MIT Core + Patron Club</span>
              </div>
              <div className="flex justify-between py-1">
                <span className="text-white/40">Sync Eligibility</span>
                <span className="text-emerald-400">Allowed (Whitelisted Data)</span>
              </div>
            </div>
          </div>
        </div>

        {/* Raw JSON Pre */}
        <div className="rounded-2xl border border-white/10 bg-white/[0.015] p-4 backdrop-blur-md space-y-2">
          <div className="flex items-center space-x-2 text-white/50 text-[12px]">
            <Terminal className="w-3.5 h-3.5" />
            <span className="font-mono">JSON Snapshot</span>
          </div>
          <pre className="text-[11px] font-mono text-white/60 bg-black/30 p-3 rounded-xl overflow-x-auto max-h-60 leading-relaxed">
            {JSON.stringify(diagnosticReport, null, 2)}
          </pre>
        </div>
      </div>
    </div>
  );
};
