import React from 'react';
import {
  Settings,
  Search,
  Palette,
  Shield,
  RotateCcw,
  Info,
  Check,
  ExternalLink,
  Github,
  Globe,
  Layers,
} from 'lucide-react';
import {
  BrowserSettings,
  SearchEngine,
  ThemeMode,
} from '../../types/browser';

interface SettingsPageProps {
  settings: BrowserSettings;
  onUpdateSettings: (updates: Partial<BrowserSettings>) => void;
  onNavigate: (url: string) => void;
}

export const SettingsPage: React.FC<SettingsPageProps> = ({
  settings,
  onUpdateSettings,
  onNavigate,
}) => {
  const searchEngines: { id: SearchEngine; name: string; desc: string; domain: string }[] = [
    {
      id: 'DuckDuckGo',
      name: 'DuckDuckGo',
      desc: 'Privacy-first search with zero personal tracking',
      domain: 'duckduckgo.com',
    },
    {
      id: 'Google',
      name: 'Google',
      desc: 'Comprehensive web indexing & search results',
      domain: 'google.com',
    },
    {
      id: 'Bing',
      name: 'Microsoft Bing',
      desc: 'Fast web search with intelligent summarization',
      domain: 'bing.com',
    },
    {
      id: 'Ecosia',
      name: 'Ecosia',
      desc: 'Tree-planting search engine dedicated to climate action',
      domain: 'ecosia.org',
    },
    {
      id: 'Kagi',
      name: 'Kagi',
      desc: 'Fast, ad-free private premium search',
      domain: 'kagi.com',
    },
  ];

  const themes: {
    id: ThemeMode;
    name: string;
    description: string;
    bg: string;
    accent: string;
    border: string;
  }[] = [
    {
      id: 'dark',
      name: 'Dark',
      description: 'Default minimal Obsidian black palette',
      bg: '#0e0e0e',
      accent: '#f5f5f5',
      border: '#2e2e2e',
    },
    {
      id: 'light',
      name: 'Light',
      description: 'Clean crisp porcelain paper palette',
      bg: '#fbfbfb',
      accent: '#111111',
      border: '#dedede',
    },
    {
      id: 'sunrise',
      name: 'Sunrise',
      description: 'Warm gold & toasted ivory aesthetic',
      bg: '#17130f',
      accent: '#d4ad66',
      border: '#4a3e31',
    },
    {
      id: 'patron',
      name: 'Patron Gold',
      description: 'Deep espresso with gilded brass accents',
      bg: '#0c0a08',
      accent: '#e5be73',
      border: '#3b3022',
    },
  ];

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl space-y-8 pb-12">
        {/* Header */}
        <div className="flex items-center space-x-3 pb-4 border-b border-white/10">
          <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
            <Settings className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-white/95">Settings</h1>
            <p className="text-[12px] text-white/40">
              Configure search engine, theme appearance, shields, and startup preferences
            </p>
          </div>
        </div>

        {/* 1. SEARCH ENGINE */}
        <section className="space-y-3">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <Search className="w-4 h-4 text-[#d4ad66]" />
            <span>Search Engine</span>
          </div>
          <p className="text-[12px] text-white/50">
            Choose the default search engine used for queries typed in the Floating Island and start page.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1">
            {searchEngines.map((engine) => {
              const isSelected = settings.searchEngine === engine.id;
              return (
                <div
                  key={engine.id}
                  onClick={() => onUpdateSettings({ searchEngine: engine.id })}
                  className={`flex items-start justify-between p-3.5 rounded-2xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'bg-white/10 border-[#d4ad66]/50 shadow-md ring-1 ring-[#d4ad66]/30'
                      : 'bg-white/[0.02] hover:bg-white/[0.06] border-white/5'
                  }`}
                >
                  <div className="min-w-0 pr-2">
                    <div className="flex items-center space-x-2 mb-1">
                      <span className="text-[13px] font-medium text-white/90">{engine.name}</span>
                      <span className="text-[11px] text-white/30 font-mono">({engine.domain})</span>
                    </div>
                    <p className="text-[11px] text-white/40 leading-snug">{engine.desc}</p>
                  </div>
                  {isSelected && (
                    <div className="p-1 rounded-full bg-[#d4ad66]/20 text-[#d4ad66] shrink-0 mt-0.5">
                      <Check className="w-3.5 h-3.5" />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </section>

        {/* 2. THEME & APPEARANCE */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <Palette className="w-4 h-4 text-[#d4ad66]" />
            <span>Theme & Appearance</span>
          </div>
          <p className="text-[12px] text-white/50">
            Select your preferred visual style. Blanc seamlessly tints all browser chrome, internal pages, and command palettes.
          </p>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-1">
            {themes.map((t) => {
              const isSelected = settings.theme === t.id;
              return (
                <div
                  key={t.id}
                  onClick={() => onUpdateSettings({ theme: t.id })}
                  className={`group flex flex-col p-3 rounded-2xl border cursor-pointer transition-all ${
                    isSelected
                      ? 'bg-white/10 border-[#d4ad66]/60 shadow-lg ring-1 ring-[#d4ad66]/30'
                      : 'bg-white/[0.02] hover:bg-white/[0.06] border-white/5'
                  }`}
                >
                  {/* Swatch Preview Card */}
                  <div
                    style={{ backgroundColor: t.bg, borderColor: t.border }}
                    className="w-full h-16 rounded-xl border flex items-center justify-center p-2 mb-2.5 relative shadow-inner"
                  >
                    <div
                      style={{ backgroundColor: t.accent }}
                      className="w-7 h-2 rounded-full shadow-sm"
                    />
                    {isSelected && (
                      <div className="absolute top-1.5 right-1.5 p-1 rounded-full bg-[#d4ad66] text-[#12100b]">
                        <Check className="w-3 h-3 stroke-[3]" />
                      </div>
                    )}
                  </div>

                  <span className="text-[13px] font-medium text-white/90 mb-0.5">{t.name}</span>
                  <span className="text-[11px] text-white/40 leading-snug">{t.description}</span>
                </div>
              );
            })}
          </div>
        </section>

        {/* 3. PRIVACY & BLANC SHIELDS */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <Shield className="w-4 h-4 text-[#d4ad66]" />
            <span>Privacy & Shields</span>
          </div>
          <p className="text-[12px] text-white/50">
            Network-layer ad and tracker blocking rules applied directly to request pipelines.
          </p>

          <div className="rounded-2xl border border-white/5 bg-white/[0.02] divide-y divide-white/5">
            {/* Block Trackers & Ads */}
            <div className="flex items-center justify-between p-4">
              <div className="pr-4">
                <div className="flex items-center space-x-2">
                  <span className="text-[13px] font-medium text-white/90">
                    Block Trackers & Advertisements
                  </span>
                  <span className="px-1.5 py-0.2 rounded text-[10px] uppercase font-bold tracking-wider bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                    Recommended
                  </span>
                </div>
                <p className="text-[12px] text-white/40 mt-0.5">
                  Filters ad scripts, telemetry, and tracking beacons with zero browser extension latency.
                </p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  checked={settings.blockTrackersAndAds}
                  onChange={(e) => onUpdateSettings({ blockTrackersAndAds: e.target.checked })}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-white/10 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d4ad66]"></div>
              </label>
            </div>

            {/* Block 3rd Party Cookies */}
            <div className="flex items-center justify-between p-4">
              <div className="pr-4">
                <span className="text-[13px] font-medium text-white/90">
                  Block Third-Party Cookies
                </span>
                <p className="text-[12px] text-white/40 mt-0.5">
                  Prevents cross-site tracking and profiling by advertising networks.
                </p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  checked={settings.blockThirdPartyCookies}
                  onChange={(e) => onUpdateSettings({ blockThirdPartyCookies: e.target.checked })}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-white/10 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d4ad66]"></div>
              </label>
            </div>

            {/* Strict HTTPS */}
            <div className="flex items-center justify-between p-4">
              <div className="pr-4">
                <span className="text-[13px] font-medium text-white/90">
                  Automatic HTTPS Upgrade
                </span>
                <p className="text-[12px] text-white/40 mt-0.5">
                  Automatically upgrades unencrypted HTTP connections to encrypted HTTPS.
                </p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  checked={settings.strictHttps}
                  onChange={(e) => onUpdateSettings({ strictHttps: e.target.checked })}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-white/10 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d4ad66]"></div>
              </label>
            </div>
          </div>
        </section>

        {/* 4. STARTUP BEHAVIOR */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <RotateCcw className="w-4 h-4 text-[#d4ad66]" />
            <span>On Startup</span>
          </div>
          <p className="text-[12px] text-white/50">
            Choose what Blanc displays when opening the browser window.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5 pt-1">
            <div
              onClick={() => onUpdateSettings({ startupBehavior: 'newtab' })}
              className={`p-3.5 rounded-2xl border cursor-pointer transition-all flex items-start space-x-3 ${
                settings.startupBehavior === 'newtab'
                  ? 'bg-white/10 border-[#d4ad66]/50 ring-1 ring-[#d4ad66]/30'
                  : 'bg-white/[0.02] hover:bg-white/[0.06] border-white/5'
              }`}
            >
              <div className="p-2 rounded-xl bg-white/5 text-white/70 shrink-0">
                <Globe className="w-4 h-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-medium text-white/90">Open New Tab Page</span>
                  {settings.startupBehavior === 'newtab' && (
                    <Check className="w-4 h-4 text-[#d4ad66]" />
                  )}
                </div>
                <p className="text-[11px] text-white/40 mt-0.5">
                  Start fresh with the Blanc Sunrise dashboard and your favorites.
                </p>
              </div>
            </div>

            <div
              onClick={() => onUpdateSettings({ startupBehavior: 'restore' })}
              className={`p-3.5 rounded-2xl border cursor-pointer transition-all flex items-start space-x-3 ${
                settings.startupBehavior === 'restore'
                  ? 'bg-white/10 border-[#d4ad66]/50 ring-1 ring-[#d4ad66]/30'
                  : 'bg-white/[0.02] hover:bg-white/[0.06] border-white/5'
              }`}
            >
              <div className="p-2 rounded-xl bg-white/5 text-white/70 shrink-0">
                <Layers className="w-4 h-4" />
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between">
                  <span className="text-[13px] font-medium text-white/90">
                    Restore Previous Session
                  </span>
                  {settings.startupBehavior === 'restore' && (
                    <Check className="w-4 h-4 text-[#d4ad66]" />
                  )}
                </div>
                <p className="text-[11px] text-white/40 mt-0.5">
                  Automatically reopen all tabs you had open during your previous session.
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* 5. ABOUT BLANC */}
        <section className="space-y-4 pt-4 border-t border-white/10">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <Info className="w-4 h-4 text-[#d4ad66]" />
            <span>About Blanc</span>
          </div>

          <div className="p-5 rounded-2xl bg-white/[0.03] border border-white/5 space-y-4">
            <div className="flex items-center space-x-4">
              <div className="w-12 h-12 rounded-xl bg-[#d4ad66]/10 border border-[#d4ad66]/20 flex items-center justify-center p-2">
                <img
                  src="/sunrise-hero-mark.png"
                  alt="Blanc"
                  className="w-8 h-8 object-contain"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                  }}
                />
              </div>
              <div>
                <h3 className="text-[15px] font-bold text-white/95">Blanc Browser</h3>
                <p className="text-[12px] text-white/50">
                  Version 1.27.0 (Mac build 1274) • Tauri Engine v2.0
                </p>
              </div>
            </div>

            <p className="text-[12px] text-white/60 leading-relaxed">
              Blanc is an open-source, minimal browser shell built around the Bowser Design System's
              Floating Island Chrome. Ad and tracker blocking is integrated at the network layer,
              protecting your privacy without the limitations of Chrome extension stores.
            </p>

            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 text-[11px] text-white/50 space-y-1">
              <p className="font-semibold text-white/70">Licensing Notice:</p>
              <p>
                Released under the <strong>MIT License</strong> for Bananify Creative-owned software.
                The Blanc and Bananify Creative names, logos, and identity assets remain reserved.
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-4 pt-1 text-[12px]">
              <button
                onClick={() => onNavigate('https://blancbrowser.com')}
                className="flex items-center space-x-1.5 text-[#d4ad66] hover:underline"
              >
                <Globe className="w-3.5 h-3.5" />
                <span>blancbrowser.com</span>
                <ExternalLink className="w-2.5 h-2.5 opacity-60" />
              </button>

              <button
                onClick={() => onNavigate('https://github.com/bnfy/blanc')}
                className="flex items-center space-x-1.5 text-white/70 hover:text-white"
              >
                <Github className="w-3.5 h-3.5" />
                <span>GitHub Repository</span>
                <ExternalLink className="w-2.5 h-2.5 opacity-60" />
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
