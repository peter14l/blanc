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
  Type,
  Image as ImageIcon,
  MousePointer,
  Camera,
  Mic,
  Bell,
  MapPin,
} from 'lucide-react';
import {
  BrowserSettings,
  SearchEngine,
  ThemeMode,
} from '../../types/browser';
import { FontSwitcher } from '../FontSwitcher';
import { NATURE_WALLPAPERS, applyWallpaperTheme } from '../../data/natureWallpapers';

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

        {/* 3. TYPOGRAPHY & INBUILT FONT SWITCHER */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
              <Type className="w-4 h-4 text-[#d4ad66]" />
              <span>Typography & Google Fonts</span>
            </div>
            <FontSwitcher
              currentFont={settings.fontFamily || 'Inter'}
              onSelectFont={(font) => onUpdateSettings({ fontFamily: font })}
            />
          </div>
          <p className="text-[12px] text-white/50">
            Customize the browser's UI font family across the Floating Island, New Tab dashboard, and surfaces.
          </p>

          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/5 space-y-2">
            <div className="flex items-center justify-between text-[11px] text-white/40">
              <span>Active Font: <strong className="text-white/80">{settings.fontFamily || 'Inter'}</strong></span>
              <span className="font-mono text-[10px]">Google Fonts Catalog</span>
            </div>
            <p className="text-[14px] text-white/80 leading-relaxed font-ui">
              "Minimalism is not a lack of something. It's simply the perfect amount of everything."
            </p>
          </div>
        </section>

        {/* 4. NATURE WALLPAPERS (START PAGE) */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
              <ImageIcon className="w-4 h-4 text-[#d4ad66]" />
              <span>Nature Wallpapers from the Web</span>
            </div>
            <label className="relative inline-flex items-center cursor-pointer shrink-0">
              <input
                type="checkbox"
                checked={settings.natureWallpaper !== false}
                onChange={(e) => onUpdateSettings({ natureWallpaper: e.target.checked })}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-white/10 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d4ad66]"></div>
            </label>
          </div>
          <p className="text-[12px] text-white/50">
            Set breathtaking high-resolution nature photography as the background on new tabs. The browser frame and Island pill automatically adapt their accent colors and glow to the active image.
          </p>

          {settings.natureWallpaper !== false && (
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2">
              {NATURE_WALLPAPERS.map((wp) => {
                const isSelected = settings.wallpaperId === wp.id || (!settings.wallpaperId && wp.id === 'emerald-lake');
                return (
                  <div
                    key={wp.id}
                    onClick={() => {
                      applyWallpaperTheme(wp);
                      onUpdateSettings({ wallpaperId: wp.id });
                    }}
                    className={`group relative rounded-2xl overflow-hidden border cursor-pointer transition-all aspect-video ${
                      isSelected
                        ? 'border-[#d4ad66] ring-2 ring-[#d4ad66]/40 scale-[1.02]'
                        : 'border-white/10 hover:border-white/30'
                    }`}
                  >
                    <img
                      src={wp.thumbnailUrl}
                      alt={wp.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/20 to-transparent p-2 flex flex-col justify-end">
                      <span className="text-[11px] font-medium text-white truncate">{wp.title}</span>
                      <span className="text-[9px] text-white/60 truncate">{wp.location}</span>
                    </div>
                    {isSelected && (
                      <div className="absolute top-2 right-2 p-1 rounded-full bg-[#d4ad66] text-black">
                        <Check className="w-3 h-3 stroke-[3]" />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* 5. MOUSE GESTURES */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
              <MousePointer className="w-4 h-4 text-[#d4ad66]" />
              <span>Mouse Gestures & Rocker Navigation</span>
            </div>
            <label className="relative inline-flex items-center cursor-pointer shrink-0">
              <input
                type="checkbox"
                checked={settings.mouseGesturesEnabled !== false}
                onChange={(e) => onUpdateSettings({ mouseGesturesEnabled: e.target.checked })}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-white/10 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#d4ad66]"></div>
            </label>
          </div>
          <p className="text-[12px] text-white/50">
            Navigate rapidly between pages by right-clicking and dragging mouse gestures or using mouse rocker controls.
          </p>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 pt-1 text-[12px]">
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[16px] text-[#d4ad66] font-bold">←</span>
              <div>
                <p className="font-medium text-white/90">Right Drag Left</p>
                <p className="text-[10px] text-white/40">Go Back</p>
              </div>
            </div>
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[16px] text-[#d4ad66] font-bold">→</span>
              <div>
                <p className="font-medium text-white/90">Right Drag Right</p>
                <p className="text-[10px] text-white/40">Go Forward</p>
              </div>
            </div>
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[16px] text-emerald-400 font-bold">↑</span>
              <div>
                <p className="font-medium text-white/90">Right Drag Up</p>
                <p className="text-[10px] text-white/40">New Tab</p>
              </div>
            </div>
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[16px] text-rose-400 font-bold">↓</span>
              <div>
                <p className="font-medium text-white/90">Right Drag Down</p>
                <p className="text-[10px] text-white/40">Close Tab</p>
              </div>
            </div>
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[16px] text-sky-400 font-bold">↻</span>
              <div>
                <p className="font-medium text-white/90">Right Drag Up/Down</p>
                <p className="text-[10px] text-white/40">Reload Page</p>
              </div>
            </div>
            <div className="p-3 rounded-xl bg-white/[0.02] border border-white/5 flex items-center gap-3">
              <span className="font-mono text-[13px] text-purple-400 font-bold">Rocker</span>
              <div>
                <p className="font-medium text-white/90">Hold Right + Left</p>
                <p className="text-[10px] text-white/40">Instant Back</p>
              </div>
            </div>
          </div>
        </section>

        {/* 6. PRIVACY & BLANC SHIELDS */}
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

        {/* 7. SITE PERMISSIONS & MEDIA ACCESS */}
        <section className="space-y-3 pt-4 border-t border-white/10">
          <div className="flex items-center space-x-2 text-[14px] font-semibold text-white/90">
            <Camera className="w-4 h-4 text-[#d4ad66]" />
            <span>Site Permissions & Media Access</span>
          </div>
          <p className="text-[12px] text-white/50">
            Control default access rules for camera, microphone, notifications, and location requests made by websites (such as Instagram Web calls, Google Meet, or Discord).
          </p>

          <div className="rounded-2xl border border-white/5 bg-white/[0.02] divide-y divide-white/5">
            {/* Camera */}
            <div className="flex items-center justify-between p-4">
              <div className="flex items-center space-x-3">
                <div className="p-2 rounded-xl bg-white/5 text-white/70">
                  <Camera className="w-4 h-4 text-emerald-400" />
                </div>
                <div>
                  <span className="text-[13px] font-medium text-white/90">Camera Access</span>
                  <p className="text-[11px] text-white/40">Used for video calls, avatar captures, and WebRTC streaming</p>
                </div>
              </div>
              <select
                value={settings.cameraPermission || 'ask'}
                onChange={(e) => onUpdateSettings({ cameraPermission: e.target.value as any })}
                className="bg-black/60 border border-white/10 rounded-xl px-3 py-1.5 text-[12px] text-white/90 focus:outline-none focus:border-[#d4ad66]/50"
              >
                <option value="ask" className="bg-[#121212] text-white">Ask each time (Default)</option>
                <option value="allow" className="bg-[#121212] text-white">Always Allow</option>
                <option value="block" className="bg-[#121212] text-white">Always Block</option>
              </select>
            </div>

            {/* Microphone */}
            <div className="flex items-center justify-between p-4">
              <div className="flex items-center space-x-3">
                <div className="p-2 rounded-xl bg-white/5 text-white/70">
                  <Mic className="w-4 h-4 text-sky-400" />
                </div>
                <div>
                  <span className="text-[13px] font-medium text-white/90">Microphone Access</span>
                  <p className="text-[11px] text-white/40">Used for audio calls, voice messages, and voice input</p>
                </div>
              </div>
              <select
                value={settings.microphonePermission || 'ask'}
                onChange={(e) => onUpdateSettings({ microphonePermission: e.target.value as any })}
                className="bg-black/60 border border-white/10 rounded-xl px-3 py-1.5 text-[12px] text-white/90 focus:outline-none focus:border-[#d4ad66]/50"
              >
                <option value="ask" className="bg-[#121212] text-white">Ask each time (Default)</option>
                <option value="allow" className="bg-[#121212] text-white">Always Allow</option>
                <option value="block" className="bg-[#121212] text-white">Always Block</option>
              </select>
            </div>

            {/* Notifications */}
            <div className="flex items-center justify-between p-4">
              <div className="flex items-center space-x-3">
                <div className="p-2 rounded-xl bg-white/5 text-white/70">
                  <Bell className="w-4 h-4 text-amber-400" />
                </div>
                <div>
                  <span className="text-[13px] font-medium text-white/90">Web Notifications</span>
                  <p className="text-[11px] text-white/40">Sites can push system notifications for incoming messages and alerts</p>
                </div>
              </div>
              <select
                value={settings.notificationsPermission || 'ask'}
                onChange={(e) => onUpdateSettings({ notificationsPermission: e.target.value as any })}
                className="bg-black/60 border border-white/10 rounded-xl px-3 py-1.5 text-[12px] text-white/90 focus:outline-none focus:border-[#d4ad66]/50"
              >
                <option value="ask" className="bg-[#121212] text-white">Ask each time (Default)</option>
                <option value="allow" className="bg-[#121212] text-white">Always Allow</option>
                <option value="block" className="bg-[#121212] text-white">Always Block</option>
              </select>
            </div>

            {/* Geolocation */}
            <div className="flex items-center justify-between p-4">
              <div className="flex items-center space-x-3">
                <div className="p-2 rounded-xl bg-white/5 text-white/70">
                  <MapPin className="w-4 h-4 text-purple-400" />
                </div>
                <div>
                  <span className="text-[13px] font-medium text-white/90">Location & Geolocation</span>
                  <p className="text-[11px] text-white/40">Provides accurate geographic position to mapping and weather services</p>
                </div>
              </div>
              <select
                value={settings.geolocationPermission || 'ask'}
                onChange={(e) => onUpdateSettings({ geolocationPermission: e.target.value as any })}
                className="bg-black/60 border border-white/10 rounded-xl px-3 py-1.5 text-[12px] text-white/90 focus:outline-none focus:border-[#d4ad66]/50"
              >
                <option value="ask" className="bg-[#121212] text-white">Ask each time (Default)</option>
                <option value="allow" className="bg-[#121212] text-white">Always Allow</option>
                <option value="block" className="bg-[#121212] text-white">Always Block</option>
              </select>
            </div>
          </div>
        </section>

        {/* 8. STARTUP BEHAVIOR */}
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
