import React, { useEffect, useRef } from 'react';
import { Tab } from '../types/browser';
import {
  Globe,
  ShieldCheck,
  RotateCw,
  X,
  Search,
  Sparkles,
  History,
  Bookmark as BookmarkIcon,
  Settings,
  Download,
  Keyboard,
  Activity,
  Lock,
  Moon,
  Play,
  Code,
} from 'lucide-react';

interface TabSwitcherHUDProps {
  isOpen: boolean;
  tabs: Tab[];
  selectedIndex: number;
  onSelectIndex: (index: number) => void;
  onConfirm: (tabId: string) => void;
  onClose: () => void;
  onCloseTab?: (tabId: string) => void;
}

/**
 * High-fidelity thumbnail preview for a tab viewport.
 * Renders an actual thumbnail if available, an authentic mini replica for internal
 * blanc:// surfaces, or a bespoke website viewport preview for external URLs.
 */
const TabThumbnailPreview: React.FC<{ tab: Tab; isSelected: boolean }> = ({
  tab,
  isSelected,
}) => {
  const url = tab.url || '';

  // 1. If real screenshot / thumbnail image is stored on tab
  if (tab.thumbnail) {
    return (
      <div className="w-full h-full relative overflow-hidden bg-black/50">
        <img
          src={tab.thumbnail}
          alt={tab.title}
          className="w-full h-full object-cover object-top"
          onError={(e) => {
            e.currentTarget.style.display = 'none';
          }}
        />
      </div>
    );
  }

  // 2. INTERNAL SURFACES: High-fidelity mini representations
  if (url === 'blanc://newtab' || url === 'about:blank' || !url) {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#181818] via-[#101010] to-[#0c0c0c] flex flex-col items-center justify-between p-2.5 overflow-hidden">
        {/* Subtle warm sunrise ambient glow */}
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_40%,rgba(212,173,102,0.12),transparent_70%)] pointer-events-none" />

        {/* Center: Hero Mark & Search Capsule */}
        <div className="my-auto flex flex-col items-center space-y-1.5 z-10 w-full px-2">
          <img
            src="/sunrise-hero-mark.png"
            alt="Blanc"
            className="w-5 h-5 object-contain filter drop-shadow-[0_2px_8px_rgba(212,173,102,0.3)]"
            onError={(e) => {
              e.currentTarget.style.display = 'none';
            }}
          />
          {/* Mini Search Pill */}
          <div className="w-full max-w-[120px] h-4 rounded-full bg-white/[0.08] border border-white/10 flex items-center px-1.5 space-x-1 shadow-sm">
            <Search className="w-2.5 h-2.5 text-white/40 shrink-0" />
            <div className="h-1 w-12 rounded bg-white/20" />
          </div>
          {/* Mini Favorite Tiles */}
          <div className="flex items-center space-x-1 pt-0.5">
            <span className="w-3.5 h-3.5 rounded bg-white/[0.06] border border-white/10 flex items-center justify-center text-[7px] text-white/50">G</span>
            <span className="w-3.5 h-3.5 rounded bg-white/[0.06] border border-white/10 flex items-center justify-center text-[7px] text-white/50">Y</span>
            <span className="w-3.5 h-3.5 rounded bg-white/[0.06] border border-white/10 flex items-center justify-center text-[7px] text-white/50">W</span>
            <span className="w-3.5 h-3.5 rounded bg-white/[0.06] border border-white/10 flex items-center justify-center text-[7px] text-white/50">R</span>
          </div>
        </div>

        {/* Bottom Tag */}
        <div className="z-10 flex items-center space-x-1 text-[9px] text-[#d4ad66]/80 font-mono">
          <Sparkles className="w-2.5 h-2.5" />
          <span>New Tab</span>
        </div>
      </div>
    );
  }

  if (url === 'blanc://history') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#13121d] to-[#0c0c12] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-purple-400 text-[9.5px] font-semibold border-b border-purple-500/20 pb-1">
          <History className="w-3 h-3" />
          <span>Browsing History</span>
        </div>
        <div className="space-y-1 my-auto">
          <div className="h-3 rounded bg-white/[0.05] border border-white/5 flex items-center px-1.5 justify-between">
            <div className="h-1.5 w-16 rounded bg-white/25" />
            <div className="h-1.5 w-6 rounded bg-white/15" />
          </div>
          <div className="h-3 rounded bg-white/[0.05] border border-white/5 flex items-center px-1.5 justify-between">
            <div className="h-1.5 w-20 rounded bg-white/25" />
            <div className="h-1.5 w-6 rounded bg-white/15" />
          </div>
          <div className="h-3 rounded bg-white/[0.05] border border-white/5 flex items-center px-1.5 justify-between">
            <div className="h-1.5 w-14 rounded bg-white/25" />
            <div className="h-1.5 w-6 rounded bg-white/15" />
          </div>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://history</span>
      </div>
    );
  }

  if (url === 'blanc://bookmarks') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#18140e] to-[#0d0c0a] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-amber-400 text-[9.5px] font-semibold border-b border-amber-500/20 pb-1">
          <BookmarkIcon className="w-3 h-3" />
          <span>Saved Bookmarks</span>
        </div>
        <div className="grid grid-cols-2 gap-1 my-auto">
          <div className="h-5 rounded bg-white/[0.05] border border-amber-500/20 p-1 flex items-center space-x-1">
            <div className="w-2.5 h-2.5 rounded bg-amber-500/30" />
            <div className="h-1.5 w-10 rounded bg-white/30" />
          </div>
          <div className="h-5 rounded bg-white/[0.05] border border-amber-500/20 p-1 flex items-center space-x-1">
            <div className="w-2.5 h-2.5 rounded bg-amber-500/30" />
            <div className="h-1.5 w-10 rounded bg-white/30" />
          </div>
          <div className="h-5 rounded bg-white/[0.05] border border-amber-500/20 p-1 flex items-center space-x-1">
            <div className="w-2.5 h-2.5 rounded bg-amber-500/30" />
            <div className="h-1.5 w-10 rounded bg-white/30" />
          </div>
          <div className="h-5 rounded bg-white/[0.05] border border-amber-500/20 p-1 flex items-center space-x-1">
            <div className="w-2.5 h-2.5 rounded bg-amber-500/30" />
            <div className="h-1.5 w-10 rounded bg-white/30" />
          </div>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://bookmarks</span>
      </div>
    );
  }

  if (url === 'blanc://settings') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#161616] to-[#0d0d0d] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-white/80 text-[9.5px] font-semibold border-b border-white/10 pb-1">
          <Settings className="w-3 h-3 text-[#d4ad66]" />
          <span>Settings & Themes</span>
        </div>
        <div className="space-y-1.5 my-auto">
          <div className="flex items-center justify-between px-1">
            <div className="h-1.5 w-14 rounded bg-white/40" />
            <div className="w-4 h-2 rounded-full bg-[#d4ad66]/40" />
          </div>
          <div className="flex items-center justify-between px-1">
            <div className="h-1.5 w-18 rounded bg-white/40" />
            <div className="w-4 h-2 rounded-full bg-white/20" />
          </div>
          <div className="flex items-center space-x-1 px-1">
            <span className="w-3 h-3 rounded-full bg-[#0e0e0e] border border-white/20" />
            <span className="w-3 h-3 rounded-full bg-[#fbfbfb] border border-white/20" />
            <span className="w-3 h-3 rounded-full bg-[#d4ad66] border border-white/20" />
          </div>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://settings</span>
      </div>
    );
  }

  if (url === 'blanc://downloads') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#11161d] to-[#0a0d12] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-blue-400 text-[9.5px] font-semibold border-b border-blue-500/20 pb-1">
          <Download className="w-3 h-3" />
          <span>Downloads</span>
        </div>
        <div className="my-auto space-y-1">
          <div className="h-4 rounded bg-white/[0.05] border border-white/5 p-1 flex items-center justify-between">
            <div className="h-1.5 w-16 rounded bg-white/30" />
            <div className="w-8 h-1 rounded bg-blue-500/60" />
          </div>
          <div className="h-4 rounded bg-white/[0.05] border border-white/5 p-1 flex items-center justify-between">
            <div className="h-1.5 w-12 rounded bg-white/30" />
            <div className="w-8 h-1 rounded bg-emerald-500/60" />
          </div>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://downloads</span>
      </div>
    );
  }

  if (url === 'blanc://shortcuts') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#141814] to-[#0c0e0c] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-emerald-400 text-[9.5px] font-semibold border-b border-emerald-500/20 pb-1">
          <Keyboard className="w-3 h-3" />
          <span>Shortcuts Guide</span>
        </div>
        <div className="my-auto space-y-1">
          <div className="flex items-center justify-between px-1">
            <div className="h-1.5 w-14 rounded bg-white/30" />
            <span className="px-1 py-0.2 rounded bg-white/10 text-[7px] font-mono text-white/60">Ctrl+T</span>
          </div>
          <div className="flex items-center justify-between px-1">
            <div className="h-1.5 w-16 rounded bg-white/30" />
            <span className="px-1 py-0.2 rounded bg-white/10 text-[7px] font-mono text-white/60">Ctrl+K</span>
          </div>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://shortcuts</span>
      </div>
    );
  }

  if (url === 'blanc://diagnostics') {
    return (
      <div className="w-full h-full relative bg-gradient-to-b from-[#111917] to-[#0a0f0e] p-2 flex flex-col justify-between overflow-hidden">
        <div className="flex items-center space-x-1 text-teal-400 text-[9.5px] font-semibold border-b border-teal-500/20 pb-1">
          <Activity className="w-3 h-3" />
          <span>Diagnostics Console</span>
        </div>
        <div className="my-auto flex items-center justify-center space-x-2">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-ping" />
          <span className="text-[10px] text-emerald-300 font-mono">Engine Normal</span>
        </div>
        <span className="text-[8.5px] text-white/30 font-mono">blanc://diagnostics</span>
      </div>
    );
  }

  // 3. EXTERNAL WEBPAGES (http / https):
  // Inspect domain to provide brand-aligned miniature viewport styling
  let hostname = '';
  try {
    hostname = new URL(url).hostname.toLowerCase().replace(/^www\./, '');
  } catch {
    hostname = url.replace(/^https?:\/\//, '').split('/')[0] || 'Web Page';
  }

  // Specific domain presets
  if (hostname.includes('google.')) {
    return (
      <div className="w-full h-full bg-[#1b1b1b] flex flex-col justify-between p-2 relative overflow-hidden">
        {/* Top mini address indicator */}
        <div className="w-full flex items-center justify-between text-[8px] text-white/35 font-mono border-b border-white/5 pb-1">
          <span className="flex items-center space-x-1">
            <Lock className="w-2 h-2 text-emerald-400" />
            <span>google.com</span>
          </span>
          <span>Search</span>
        </div>
        <div className="my-auto flex flex-col items-center space-y-1.5">
          <div className="flex items-center space-x-0.5 text-[12px] font-bold tracking-tight">
            <span className="text-[#4285F4]">G</span>
            <span className="text-[#EA4335]">o</span>
            <span className="text-[#FBBC05]">o</span>
            <span className="text-[#4285F4]">g</span>
            <span className="text-[#34A853]">l</span>
            <span className="text-[#EA4335]">e</span>
          </div>
          <div className="w-32 h-3.5 rounded-full bg-white/10 border border-white/15 flex items-center px-1.5 space-x-1">
            <Search className="w-2 h-2 text-white/40" />
            <span className="text-[8px] text-white/60 truncate max-w-[80px]">
              {tab.title.replace(/ - Google Search$/i, '')}
            </span>
          </div>
        </div>
        <div className="w-full flex justify-center space-x-1">
          <div className="w-12 h-2 rounded bg-white/5" />
          <div className="w-12 h-2 rounded bg-white/5" />
        </div>
      </div>
    );
  }

  if (hostname.includes('youtube.')) {
    return (
      <div className="w-full h-full bg-[#0f0f0f] flex flex-col justify-between p-2 relative overflow-hidden">
        <div className="w-full flex items-center justify-between text-[8px] text-white/35 font-mono border-b border-white/5 pb-1">
          <span className="flex items-center space-x-1">
            <Lock className="w-2 h-2 text-emerald-400" />
            <span>youtube.com</span>
          </span>
          <span className="text-red-500 flex items-center space-x-0.5 font-bold">
            <Play className="w-2 h-2 fill-current" />
            <span>Watch</span>
          </span>
        </div>
        <div className="my-auto flex flex-col items-center justify-center space-y-1">
          <div className="w-24 h-12 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center relative shadow-inner">
            <div className="w-6 h-6 rounded-full bg-red-600/80 flex items-center justify-center text-white shadow-lg">
              <Play className="w-3 h-3 fill-current ml-0.5" />
            </div>
            <span className="absolute bottom-1 right-1 text-[7px] font-mono px-1 rounded bg-black/80 text-white/90">
              HD
            </span>
          </div>
          <p className="text-[8.5px] font-medium text-white/80 truncate max-w-[140px] text-center">
            {tab.title.replace(/ - YouTube$/i, '')}
          </p>
        </div>
        <div className="w-full h-1 bg-red-600/40 rounded-full overflow-hidden">
          <div className="w-1/3 h-full bg-red-600" />
        </div>
      </div>
    );
  }

  if (hostname.includes('github.')) {
    return (
      <div className="w-full h-full bg-[#0d1117] flex flex-col justify-between p-2 relative overflow-hidden">
        <div className="w-full flex items-center justify-between text-[8px] text-white/35 font-mono border-b border-white/5 pb-1">
          <span className="flex items-center space-x-1">
            <Lock className="w-2 h-2 text-emerald-400" />
            <span>github.com</span>
          </span>
          <span className="flex items-center space-x-1 text-white/60">
            <Code className="w-2 h-2" />
            <span>Repository</span>
          </span>
        </div>
        <div className="my-auto space-y-1">
          <div className="flex items-center space-x-1.5">
            <div className="w-3.5 h-3.5 rounded bg-white/10 flex items-center justify-center text-[8px] font-mono text-white/80">
              ⌘
            </div>
            <p className="text-[9.5px] font-semibold text-white/95 truncate max-w-[130px]">
              {tab.title.replace(/ · GitHub$/i, '')}
            </p>
          </div>
          <div className="p-1 rounded bg-black/40 border border-white/5 space-y-0.5 font-mono">
            <div className="h-1 w-20 rounded bg-emerald-400/30" />
            <div className="h-1 w-28 rounded bg-white/15" />
            <div className="h-1 w-16 rounded bg-white/10" />
          </div>
        </div>
        <div className="flex items-center space-x-2 text-[7.5px] text-white/40 font-mono">
          <span>main</span>
          <span>•</span>
          <span>commit</span>
        </div>
      </div>
    );
  }

  if (hostname.includes('wikipedia.')) {
    return (
      <div className="w-full h-full bg-[#1c1c1f] flex flex-col justify-between p-2 relative overflow-hidden">
        <div className="w-full flex items-center justify-between text-[8px] text-white/35 font-mono border-b border-white/5 pb-1">
          <span className="flex items-center space-x-1">
            <Lock className="w-2 h-2 text-emerald-400" />
            <span>wikipedia.org</span>
          </span>
          <span className="font-serif italic">The Free Encyclopedia</span>
        </div>
        <div className="my-auto space-y-1 px-1">
          <div className="flex items-center space-x-1.5">
            <span className="font-serif text-[13px] font-bold text-white/90 leading-none">W</span>
            <p className="text-[9.5px] font-serif font-semibold text-white/90 truncate max-w-[130px]">
              {tab.title.replace(/ - Wikipedia$/i, '')}
            </p>
          </div>
          <div className="space-y-0.5">
            <div className="h-1 w-full rounded bg-white/20" />
            <div className="h-1 w-5/6 rounded bg-white/15" />
            <div className="h-1 w-4/6 rounded bg-white/10" />
          </div>
        </div>
        <span className="text-[8px] text-white/30 font-serif">Article Preview</span>
      </div>
    );
  }

  // 4. GENERAL WEBSITES: Sleek, responsive window canvas with ambient aura,
  // prominent favicon, domain watermark, and wireframe content skeleton
  return (
    <div className="w-full h-full bg-gradient-to-b from-[#181818] to-[#0e0e0e] flex flex-col justify-between p-2 relative overflow-hidden group-hover:from-[#1c1c1c] transition-colors">
      {/* Ambient radial accent aura */}
      <div
        className={`absolute inset-0 pointer-events-none transition-opacity duration-300 ${
          isSelected
            ? 'bg-[radial-gradient(circle_at_50%_35%,rgba(212,173,102,0.18),transparent_75%)] opacity-100'
            : 'bg-[radial-gradient(circle_at_50%_35%,rgba(255,255,255,0.06),transparent_70%)] opacity-60'
        }`}
      />

      {/* Mini top browser address pill inside preview */}
      <div className="w-full flex items-center justify-between text-[8px] text-white/40 font-mono border-b border-white/5 pb-1 relative z-10">
        <span className="flex items-center space-x-1 truncate max-w-[110px]">
          <Lock className="w-2 h-2 text-emerald-400 shrink-0" />
          <span className="truncate">{hostname}</span>
        </span>
        {tab.blocked_trackers > 0 ? (
          <span className="text-[#d4ad66] font-semibold shrink-0">
            {tab.blocked_trackers} blocked
          </span>
        ) : (
          <span className="text-white/30 shrink-0">Web</span>
        )}
      </div>

      {/* Center: Brand Favicon Badge & Title Snippet */}
      <div className="my-auto flex flex-col items-center justify-center space-y-1.5 relative z-10">
        <div className="w-8 h-8 rounded-xl bg-white/[0.08] border border-white/15 flex items-center justify-center p-1.5 shadow-md backdrop-blur-sm group-hover:scale-105 transition-transform">
          {tab.favicon ? (
            <img
              src={tab.favicon}
              alt=""
              className="w-4 h-4 rounded-sm object-contain"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />
          ) : (
            <Globe className="w-4 h-4 text-white/50" />
          )}
        </div>

        <div className="text-center px-1 max-w-[160px]">
          <p className="text-[10px] font-semibold text-white/90 truncate leading-tight">
            {tab.title || hostname}
          </p>
          <span className="text-[8px] font-mono text-white/40 truncate block mt-0.5">
            {hostname}
          </span>
        </div>
      </div>

      {/* Bottom: Simulated webpage layout wireframe */}
      <div className="w-full space-y-0.5 relative z-10 pt-1">
        <div className="flex items-center space-x-1">
          <div className="h-1 w-2/3 rounded bg-white/15" />
          <div className="h-1 w-1/3 rounded bg-[#d4ad66]/30" />
        </div>
        <div className="flex items-center space-x-1">
          <div className="h-1 w-1/2 rounded bg-white/10" />
          <div className="h-1 w-1/4 rounded bg-white/10" />
        </div>
      </div>
    </div>
  );
};

export const TabSwitcherHUD: React.FC<TabSwitcherHUDProps> = ({
  isOpen,
  tabs,
  selectedIndex,
  onSelectIndex,
  onConfirm,
  onClose,
  onCloseTab,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);

  // Auto-scroll selected card into view horizontally
  useEffect(() => {
    if (isOpen && containerRef.current) {
      const selectedEl = containerRef.current.querySelector(
        `[data-index="${selectedIndex}"]`
      );
      if (selectedEl) {
        selectedEl.scrollIntoView({
          behavior: 'smooth',
          block: 'nearest',
          inline: 'center',
        });
      }
    }
  }, [isOpen, selectedIndex]);

  // Handle horizontal mouse wheel scrolling across cards
  const handleWheel = (e: React.WheelEvent) => {
    if (containerRef.current) {
      containerRef.current.scrollLeft += e.deltaY;
    }
  };

  if (!isOpen || tabs.length === 0) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/65 backdrop-blur-md animate-fadeIn select-none font-ui"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative max-w-5xl w-[94%] p-5 rounded-3xl bg-[#141414]/95 border border-[var(--island-border-adaptive,rgba(255,255,255,0.18))] 
          shadow-[0_28px_80px_rgba(0,0,0,0.9)] flex flex-col gap-3.5 overflow-hidden"
      >
        {/* Top Header & Keyboard instructions */}
        <div className="flex items-center justify-between pb-2 border-b border-white/10">
          <div className="flex items-center space-x-3">
            <h3 className="text-[14.5px] font-semibold text-white/95 tracking-tight">
              Open Tabs
            </h3>
            <span className="text-[11.5px] font-mono px-2 py-0.5 rounded-full bg-white/10 text-[#d4ad66] font-medium">
              {selectedIndex + 1} of {tabs.length}
            </span>
          </div>

          <div className="flex items-center space-x-2 text-[11.5px] text-white/50 font-medium">
            <span>Hold</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10.5px]">
              Ctrl
            </kbd>
            <span>or</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10.5px]">
              Alt
            </kbd>
            <span>+</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10.5px]">
              Tab
            </kbd>
            <span>• Release to switch</span>
          </div>
        </div>

        {/* Tab Cards Strip with Thumbnails */}
        <div
          ref={containerRef}
          onWheel={handleWheel}
          className="flex items-center gap-3.5 overflow-x-auto py-3 px-2 scrollbar-none scroll-smooth"
        >
          {tabs.map((tab, idx) => {
            const isSelected = idx === selectedIndex;
            const isInternal =
              tab.url.startsWith('blanc://') || tab.url.startsWith('about:');

            let displayDomain = 'Blanc';
            try {
              if (tab.url && tab.url.startsWith('http')) {
                displayDomain = new URL(tab.url).hostname.replace(/^www\./, '');
              } else if (isInternal) {
                displayDomain = 'System';
              }
            } catch {
              displayDomain = 'Website';
            }

            return (
              <div
                key={tab.id}
                data-index={idx}
                onMouseEnter={() => onSelectIndex(idx)}
                onClick={() => onConfirm(tab.id)}
                className={`group relative flex flex-col justify-between rounded-2xl border transition-all duration-200 cursor-pointer 
                  shrink-0 w-[240px] sm:w-[260px] h-[185px] sm:h-[195px] overflow-hidden ${
                    isSelected
                      ? 'border-[#d4ad66] bg-[#d4ad66]/15 scale-[1.04] shadow-[0_12px_36px_rgba(212,173,102,0.35)] ring-2 ring-[#d4ad66]'
                      : 'border-white/10 bg-white/[0.04] hover:bg-white/[0.08] opacity-75 hover:opacity-100 hover:scale-[1.01]'
                  }`}
              >
                {/* 1. Mini Window Header Bar */}
                <div className="flex items-center justify-between px-3 py-2 bg-[#1c1c1c]/90 border-b border-white/10 shrink-0 z-10">
                  <div className="flex items-center space-x-2 min-w-0 flex-1 mr-2">
                    {tab.is_loading ? (
                      <RotateCw className="w-3.5 h-3.5 animate-spin text-[#d4ad66] shrink-0" />
                    ) : tab.favicon ? (
                      <img
                        src={tab.favicon}
                        alt=""
                        className="w-3.5 h-3.5 rounded-sm object-contain shrink-0"
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                    ) : (
                      <Globe className="w-3.5 h-3.5 text-white/50 shrink-0" />
                    )}
                    <span className="text-[11px] font-mono text-white/70 truncate">
                      {displayDomain}
                    </span>
                  </div>

                  <div className="flex items-center space-x-1.5 shrink-0">
                    {tab.is_asleep && (
                      <span title="Tab asleep to save memory">
                        <Moon className="w-3 h-3 text-white/40 shrink-0" />
                      </span>
                    )}

                    {tab.blocked_trackers > 0 ? (
                      <div
                        title={`${tab.blocked_trackers} trackers blocked`}
                        className="flex items-center space-x-0.5 px-1.5 py-0.2 rounded-full bg-[#d4ad66]/20 text-[#d4ad66] text-[9.5px] font-semibold"
                      >
                        <ShieldCheck className="w-2.5 h-2.5" />
                        <span>{tab.blocked_trackers}</span>
                      </div>
                    ) : (
                      <span className="text-[9.5px] font-mono text-white/35 px-1 rounded bg-white/5">
                        #{idx + 1}
                      </span>
                    )}

                    {/* Close Tab Button */}
                    {onCloseTab && tabs.length > 1 && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onCloseTab(tab.id);
                        }}
                        title="Close Tab (Ctrl+W)"
                        className="p-1 rounded-md text-white/30 hover:text-white hover:bg-rose-500/80 opacity-0 group-hover:opacity-100 transition-opacity ml-0.5"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                </div>

                {/* 2. Central Thumbnail Viewport Preview */}
                <div className="flex-1 w-full relative overflow-hidden bg-black/40">
                  <TabThumbnailPreview tab={tab} isSelected={isSelected} />
                </div>

                {/* 3. Bottom Title & URL Bar */}
                <div className="px-3 py-2 bg-[#181818]/95 border-t border-white/10 shrink-0 flex flex-col min-w-0 z-10">
                  <span
                    className={`text-[12.5px] font-medium truncate leading-tight transition-colors ${
                      isSelected ? 'text-[#d4ad66]' : 'text-white/95'
                    }`}
                  >
                    {tab.title || 'Untitled'}
                  </span>
                  <span className="text-[10.5px] text-white/40 truncate mt-0.5 font-mono">
                    {isInternal ? tab.url : tab.url.replace(/^https?:\/\//, '')}
                  </span>
                </div>
              </div>
            );
          })}
        </div>

        {/* Bottom Shortcut Helper Bar */}
        <div className="flex items-center justify-between pt-1 border-t border-white/5 text-[11px] text-white/40">
          <div className="flex items-center space-x-3">
            <span><strong className="text-white/70">Tab</strong> Next Tab</span>
            <span>•</span>
            <span><strong className="text-white/70">Shift+Tab</strong> Previous Tab</span>
            <span>•</span>
            <span><strong className="text-white/70">← →</strong> Navigate</span>
            <span>•</span>
            <span><strong className="text-white/70">↵</strong> Select</span>
            <span>•</span>
            <span><strong className="text-white/70">Esc</strong> Cancel</span>
          </div>

          <div className="flex items-center space-x-1.5 text-[#d4ad66]">
            <ShieldCheck className="w-3 h-3" />
            <span>Blanc Island Switcher</span>
          </div>
        </div>
      </div>
    </div>
  );
};
