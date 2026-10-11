import React, { useState, useEffect } from 'react';
import {
  Search,
  ShieldCheck,
  Plus,
  MoreVertical,
  Trash2,
  Edit2,
  X,
  RefreshCw,
  MapPin,
} from 'lucide-react';
import { Favorite, BrowserSettings, AdblockStats, HistoryEntry, Bookmark as BookmarkType } from '../../types/browser';
import { useSearchSuggestions, SuggestionItem } from '../../hooks/useSearchSuggestions';
import { SuggestionPicker } from '../SuggestionPicker';
import { NATURE_WALLPAPERS, NatureWallpaper, applyWallpaperTheme } from '../../data/natureWallpapers';
import { FontSwitcher } from '../FontSwitcher';

interface NewTabPageProps {
  favorites: Favorite[];
  history?: HistoryEntry[];
  bookmarks?: BookmarkType[];
  onNavigate: (url: string) => void;
  onAddFavorite: (fav: Omit<Favorite, 'id'>) => void;
  onRemoveFavorite: (id: string) => void;
  onUpdateFavorite: (id: string, updates: Partial<Favorite>) => void;
  settings: BrowserSettings;
  adblockStats: AdblockStats;
  currentTabBlockedTrackers?: number;
  onUpdateSettings?: (updates: Partial<BrowserSettings>) => void;
}

const SEARCH_ENGINES = [
  { id: 'Google', label: 'Google', icon: 'G', searchUrl: (q: string) => `https://www.google.com/search?q=${encodeURIComponent(q)}&igu=1` },
  { id: 'DuckDuckGo', label: 'DuckDuckGo', icon: 'D', searchUrl: (q: string) => `https://duckduckgo.com/?q=${encodeURIComponent(q)}` },
  { id: 'Bing', label: 'Bing', icon: 'B', searchUrl: (q: string) => `https://www.bing.com/search?q=${encodeURIComponent(q)}` },
  { id: 'Wikipedia', label: 'Wikipedia', icon: 'W', searchUrl: (q: string) => `https://en.wikipedia.org/wiki/Special:Search?search=${encodeURIComponent(q)}` },
  { id: 'YouTube', label: 'YouTube', icon: 'Y', searchUrl: (q: string) => `https://www.youtube.com/results?search_query=${encodeURIComponent(q)}` },
  { id: 'GitHub', label: 'GitHub', icon: '⌘', searchUrl: (q: string) => `https://github.com/search?q=${encodeURIComponent(q)}` },
];

const PRESET_FAVORITES = [
  { title: 'GitHub', url: 'https://github.com' },
  { title: 'YouTube', url: 'https://youtube.com' },
  { title: 'Wikipedia', url: 'https://wikipedia.org' },
  { title: 'Reddit', url: 'https://reddit.com' },
  { title: 'Hacker News', url: 'https://news.ycombinator.com' },
  { title: 'X', url: 'https://x.com' },
];

export const NewTabPage: React.FC<NewTabPageProps> = ({
  favorites,
  history = [],
  bookmarks = [],
  onNavigate,
  onAddFavorite,
  onRemoveFavorite,
  onUpdateFavorite,
  settings,
  adblockStats,
  onUpdateSettings,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [activeEngine, setActiveEngine] = useState<string>(settings.searchEngine || 'Google');
  const [dateStr, setDateStr] = useState('');

  // Nature wallpaper state (default active)
  const [wallpaperIndex, setWallpaperIndex] = useState(() => {
    if (settings.wallpaperId) {
      const idx = NATURE_WALLPAPERS.findIndex((w) => w.id === settings.wallpaperId);
      if (idx !== -1) return idx;
    }
    return 0; // Emerald Alpine Lake by default
  });

  const activeWallpaper: NatureWallpaper =
    NATURE_WALLPAPERS[wallpaperIndex] || NATURE_WALLPAPERS[0];

  // Adapt browser frame and tokens to the active wallpaper
  useEffect(() => {
    if (settings.natureWallpaper !== false) {
      applyWallpaperTheme(activeWallpaper);
    }
  }, [activeWallpaper, settings.natureWallpaper]);

  const handleNextWallpaper = () => {
    const nextIdx = (wallpaperIndex + 1) % NATURE_WALLPAPERS.length;
    setWallpaperIndex(nextIdx);
    const nextWp = NATURE_WALLPAPERS[nextIdx];
    applyWallpaperTheme(nextWp);
    onUpdateSettings?.({ wallpaperId: nextWp.id });
  };

  const [isFocused, setIsFocused] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isPickerVisible, setIsPickerVisible] = useState(false);

  const { suggestions } = useSearchSuggestions({
    query: searchQuery,
    history,
    bookmarks,
    enabled: isFocused && searchQuery.trim().length > 0,
  });

  useEffect(() => {
    if (isFocused && searchQuery.trim().length > 0 && suggestions.length > 0) {
      setIsPickerVisible(true);
      setSelectedIndex(0);
    } else {
      setIsPickerVisible(false);
    }
  }, [suggestions, isFocused, searchQuery]);

  const handleSelectSuggestion = (item: SuggestionItem) => {
    setIsPickerVisible(false);
    if (item.url) {
      onNavigate(item.url);
    } else if (item.query) {
      const engineObj =
        SEARCH_ENGINES.find((e) => e.id.toLowerCase() === activeEngine.toLowerCase()) ||
        SEARCH_ENGINES[0];
      onNavigate(engineObj.searchUrl(item.query));
    } else {
      onNavigate(item.title);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (isPickerVisible && suggestions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % suggestions.length);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length);
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        const selected = suggestions[selectedIndex];
        if (selected) {
          handleSelectSuggestion(selected);
          return;
        }
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setIsPickerVisible(false);
        return;
      }
    }
  };

  // Modal State
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingFavorite, setEditingFavorite] = useState<Favorite | null>(null);
  const [favoriteTitle, setFavoriteTitle] = useState('');
  const [favoriteUrl, setFavoriteUrl] = useState('');
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  // Update date
  useEffect(() => {
    const updateDate = () => {
      const now = new Date();
      setDateStr(
        now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
      );
    };

    updateDate();
    const interval = setInterval(updateDate, 60000);
    return () => clearInterval(interval);
  }, []);

  // Handle Search Submission
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    const q = searchQuery.trim();
    if (!q) return;

    if (q.startsWith('blanc://') || q.startsWith('http://') || q.startsWith('https://')) {
      onNavigate(q);
      return;
    }

    if (q.includes('.') && !q.includes(' ')) {
      onNavigate(`https://${q}`);
      return;
    }

    const engineObj = SEARCH_ENGINES.find((e) => e.id.toLowerCase() === activeEngine.toLowerCase()) || SEARCH_ENGINES[0];
    onNavigate(engineObj.searchUrl(q));
  };

  const openAddFavorite = () => {
    setEditingFavorite(null);
    setFavoriteTitle('');
    setFavoriteUrl('https://');
    setIsAddModalOpen(true);
  };

  const openEditFavorite = (fav: Favorite) => {
    setEditingFavorite(fav);
    setFavoriteTitle(fav.title);
    setFavoriteUrl(fav.url);
    setIsAddModalOpen(true);
    setActiveMenuId(null);
  };

  const saveFavorite = (e: React.FormEvent) => {
    e.preventDefault();
    if (!favoriteTitle.trim() || !favoriteUrl.trim()) return;

    let cleanUrl = favoriteUrl.trim();
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://') && !cleanUrl.startsWith('blanc://')) {
      cleanUrl = `https://${cleanUrl}`;
    }

    if (editingFavorite) {
      onUpdateFavorite(editingFavorite.id, {
        title: favoriteTitle.trim(),
        url: cleanUrl,
      });
    } else {
      onAddFavorite({
        title: favoriteTitle.trim(),
        url: cleanUrl,
      });
    }

    setIsAddModalOpen(false);
  };

  // Recent 4 history items
  const recentHistory = history
    .filter((h) => !h.url.startsWith('blanc://') && h.url !== 'about:blank')
    .slice(0, 4);

  return (
    <div className="w-full h-full overflow-y-auto bg-[#0e0e0e] text-[#f5f5f5] flex flex-col items-center select-none font-ui relative z-0">
      {/* OPTIONAL SUBTLE WALLPAPER (ONLY WHEN ACTIVELY ENABLED IN SETTINGS) */}
      {settings.natureWallpaper && activeWallpaper && (
        <div className="absolute inset-0 overflow-hidden pointer-events-none z-0">
          <img
            src={activeWallpaper.url}
            alt={activeWallpaper.title}
            loading="eager"
            className="absolute inset-0 w-full h-full object-cover filter brightness-[0.25] contrast-[1.05]"
          />
          <div className="absolute inset-0 bg-[#0e0e0e]/80" />
        </div>
      )}

      <div className="w-full max-w-4xl px-6 py-12 sm:py-16 flex flex-col items-center z-10 space-y-8 sm:space-y-10 relative">
        {/* 1. CANONICAL BRAND MARK & DATE HEADER */}
        <header className="flex flex-col items-center text-center space-y-3 pt-4">
          <div
            onClick={() => onNavigate('https://blancbrowser.com')}
            className="cursor-pointer group flex flex-col items-center"
            title="Blanc"
          >
            <img
              src="/sunrise-hero-mark.png"
              alt="Blanc"
              className="w-13 h-13 sm:w-14 sm:h-14 object-contain filter drop-shadow-[0_4px_16px_rgba(212,173,102,0.18)] group-hover:scale-105 transition-transform duration-200"
            />
          </div>
          <span className="text-[12px] font-medium tracking-[0.08em] text-[#8e8e8e] lowercase">
            {dateStr}
          </span>
        </header>

        {/* 2. CENTERED SEARCH BAR */}
        <div className="w-full max-w-[560px]">
          <form
            onSubmit={handleSearch}
            className="relative flex items-center h-[46px] px-4 rounded-[17px] bg-[#161616] hover:bg-[#1a1a1a] focus-within:bg-[#151515] border border-[#2a2a2a] hover:border-[#383838] focus-within:border-[#444444] shadow-[0_2px_14px_rgba(0,0,0,0.4)] transition-all group"
          >
            <Search className="w-4 h-4 text-white/35 group-focus-within:text-white/70 mr-2.5 transition-colors shrink-0" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => setIsFocused(true)}
              onBlur={() => {
                setTimeout(() => {
                  setIsFocused(false);
                  setIsPickerVisible(false);
                }, 200);
              }}
              onKeyDown={handleKeyDown}
              placeholder={`Search ${activeEngine} or enter URL...`}
              className="flex-1 bg-transparent text-[14.5px] text-white/95 placeholder-white/30 outline-none border-none font-medium"
            />

            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10 shrink-0"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}

            {/* AUTOCOMPLETE / SUGGESTION PICKER */}
            <SuggestionPicker
              suggestions={suggestions}
              selectedIndex={selectedIndex}
              onSelect={handleSelectSuggestion}
              onHoverIndex={setSelectedIndex}
              searchEngine={activeEngine}
              visible={isPickerVisible && isFocused}
            />
          </form>

          {/* ENGINE SWITCHER */}
          <div className="flex items-center justify-center space-x-1.5 mt-2.5 overflow-x-auto py-1 text-[12px]">
            {SEARCH_ENGINES.map((eng) => (
              <button
                key={eng.id}
                type="button"
                onClick={() => setActiveEngine(eng.id)}
                className={`px-2.5 py-0.5 rounded-full transition-all ${
                  activeEngine.toLowerCase() === eng.id.toLowerCase()
                    ? 'bg-white/10 text-white font-medium'
                    : 'text-white/40 hover:text-white/75 hover:bg-white/5'
                }`}
              >
                {eng.label}
              </button>
            ))}
          </div>
        </div>

        {/* 3. FAVORITES (LEDGER TILES) */}
        <div className="w-full max-w-[620px]">
          <div className="flex items-center justify-between mb-2.5 px-0.5">
            <span className="text-[11.5px] font-semibold tracking-[0.11em] uppercase text-[#777777]">
              Favorites
            </span>
            <button
              onClick={openAddFavorite}
              className="flex items-center space-x-1 text-[12.5px] text-[#8e8e8e] hover:text-[#f5f5f5] transition-colors cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add</span>
            </button>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-2.5">
            {favorites.map((fav) => {
              let domain = '';
              try {
                domain = new URL(fav.url).hostname.replace(/^www\./, '');
              } catch {
                domain = fav.url;
              }
              const faviconSrc = fav.favicon || `https://www.google.com/s2/favicons?domain=${domain}&sz=64`;

              return (
                <div
                  key={fav.id}
                  onClick={() => onNavigate(fav.url)}
                  className="p-3 rounded-xl bg-[#161616] hover:bg-[#1c1c1c] border border-[#242424] hover:border-[#333333] transition-all cursor-pointer flex flex-col items-start space-y-2 group relative text-left"
                >
                  <div className="flex items-center justify-between w-full">
                    <div className="w-8 h-8 rounded-lg bg-[#202020] border border-[#2a2a2a] flex items-center justify-center p-1.5 overflow-hidden">
                      <img
                        src={faviconSrc}
                        alt=""
                        className="w-4 h-4 object-contain"
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                          e.currentTarget.nextElementSibling?.classList.remove('hidden');
                        }}
                      />
                      <div className="hidden font-mono font-medium text-white/70 text-[12px]">
                        {fav.title[0]?.toUpperCase() || 'W'}
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActiveMenuId(activeMenuId === fav.id ? null : fav.id);
                      }}
                      className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-white/10 text-white/40 hover:text-white transition-opacity"
                    >
                      <MoreVertical className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  <div className="w-full min-w-0">
                    <span className="text-[13px] font-medium text-[#ededed] truncate block">
                      {fav.title}
                    </span>
                    <span className="text-[11.5px] text-[#777777] font-mono truncate block mt-0.5">
                      {domain}
                    </span>
                  </div>

                  {activeMenuId === fav.id && (
                    <div
                      className="absolute right-2 top-9 w-28 rounded-xl bg-[#202020] border border-[#333333] shadow-xl py-1 z-20"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        type="button"
                        onClick={() => openEditFavorite(fav)}
                        className="w-full px-3 py-1.5 text-left text-[11.5px] text-white/80 hover:bg-white/10 flex items-center space-x-2"
                      >
                        <Edit2 className="w-3 h-3" />
                        <span>Edit</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          onRemoveFavorite(fav.id);
                          setActiveMenuId(null);
                        }}
                        className="w-full px-3 py-1.5 text-left text-[11.5px] text-red-400 hover:bg-white/10 flex items-center space-x-2"
                      >
                        <Trash2 className="w-3 h-3" />
                        <span>Delete</span>
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            {/* Quick Add Button */}
            <button
              onClick={openAddFavorite}
              className="p-3 rounded-xl border border-dashed border-[#262626] hover:border-[#383838] hover:bg-[#161616]/60 transition-all text-[#666666] hover:text-[#aaaaaa] flex flex-col items-center justify-center min-h-[90px] cursor-pointer"
            >
              <Plus className="w-4 h-4 mb-1" />
              <span className="text-[11px] font-medium">Add shortcut</span>
            </button>
          </div>
        </div>

        {/* 4. RECENT ACTIVITY (PICK UP WHERE YOU LEFT OFF) */}
        {recentHistory.length > 0 && (
          <div className="w-full max-w-[620px]">
            <div className="flex items-center justify-between mb-2.5 px-0.5">
              <span className="text-[10.5px] font-semibold tracking-[0.11em] uppercase text-[#777777]">
                Pick up where you left off
              </span>
              <button
                onClick={() => onNavigate('blanc://history')}
                className="text-[11px] text-[#777777] hover:text-[#cccccc] transition-colors"
              >
                history →
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {recentHistory.map((item) => {
                let domain = '';
                try {
                  domain = new URL(item.url).hostname.replace(/^www\./, '');
                } catch {
                  domain = item.url;
                }
                return (
                  <div
                    key={item.id}
                    onClick={() => onNavigate(item.url)}
                    className="flex items-center space-x-2.5 p-2.5 rounded-xl bg-[#141414] hover:bg-[#1a1a1a] border border-[#222222] hover:border-[#2e2e2e] transition-colors cursor-pointer text-left"
                  >
                    <div className="w-7 h-7 rounded-lg bg-[#202020] flex items-center justify-center text-white/60 shrink-0 text-[11px] font-medium">
                      {item.title?.[0]?.toUpperCase() || 'W'}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-[13px] font-medium text-white/90 truncate">{item.title}</p>
                      <p className="text-[11.5px] text-[#777777] font-mono truncate">{domain}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* 5. QUIET PRIVACY STATS FOOTER */}
        <div className="flex items-center space-x-2 text-[13px] text-[#888888] pt-2">
          <ShieldCheck className="w-3.5 h-3.5 text-[#d4ad66]" />
          <span>
            <span className="font-mono text-[#f5f5f5]">{adblockStats.totalBlocked.toLocaleString()}</span> ads & trackers blocked this week
          </span>
        </div>

        {/* 6. SHORTCUT HINTS */}
        <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-1.5 text-[12px] text-[#666666] font-mono">
          <span><span className="text-[#999999]">Ctrl+K</span> Quick Switcher</span>
          <span>•</span>
          <span><span className="text-[#999999]">Ctrl+T</span> New Tab</span>
          <span>•</span>
          <span><span className="text-[#999999]">Ctrl+L</span> Address Bar</span>
          <span>•</span>
          <span><span className="text-[#999999]">Ctrl+Shift+V</span> Vertical Tabs</span>
          <span>•</span>
          <span><span className="text-[#999999]">Ctrl+,</span> Settings</span>
        </div>

        {/* BOTTOM PERSONALIZATION BAR (WALLPAPER CONTROLS & FONT SWITCHER) */}
        <div className="flex flex-wrap items-center justify-between gap-4 w-full pt-4 border-t border-[#222222] text-[13px]">
          {/* Wallpaper location & credit badge */}
          {activeWallpaper && settings.natureWallpaper ? (
            <div className="flex items-center space-x-2 text-white/50 hover:text-white/80 transition-colors">
              <MapPin className="w-3.5 h-3.5 text-[#d4ad66] shrink-0" />
              <span className="font-medium text-white/70">{activeWallpaper.location}</span>
              <span className="text-white/30">•</span>
              <span className="text-white/40">Photo by {activeWallpaper.photographer}</span>
            </div>
          ) : (
            <div />
          )}

          {/* Controls: Next Wallpaper & Font Switcher */}
          <div className="flex items-center space-x-2.5 ml-auto">
            {settings.natureWallpaper && (
              <button
                type="button"
                onClick={handleNextWallpaper}
                title="Shuffle Nature Wallpaper"
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-[#2a2a2a] text-white/80 hover:text-white transition-all active:scale-95 cursor-pointer text-[12.5px]"
              >
                <RefreshCw className="w-3.5 h-3.5 text-[#d4ad66]" />
                <span>Next Nature View</span>
              </button>
            )}

            <FontSwitcher
              currentFont={settings.fontFamily || 'Inter'}
              onSelectFont={(font) => onUpdateSettings?.({ fontFamily: font })}
              compact
            />
          </div>
        </div>
      </div>


      {/* ADD / EDIT SHORTCUT MODAL */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-black/75 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="w-full max-w-sm rounded-2xl bg-[#181818] border border-white/10 p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-white/10 pb-3">
              <h3 className="text-[15px] font-semibold text-white">
                {editingFavorite ? 'Edit Shortcut' : 'Add Shortcut'}
              </h3>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="p-1 rounded-lg text-white/40 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Preset Suggestions */}
            {!editingFavorite && (
              <div>
                <p className="text-[11px] text-white/40 mb-2">Quick presets:</p>
                <div className="flex flex-wrap gap-1.5">
                  {PRESET_FAVORITES.map((preset) => (
                    <button
                      key={preset.title}
                      type="button"
                      onClick={() => {
                        setFavoriteTitle(preset.title);
                        setFavoriteUrl(preset.url);
                      }}
                      className="px-2 py-1 rounded-lg bg-white/5 hover:bg-[#d4ad66]/20 hover:text-[#d4ad66] border border-white/5 text-[11px] text-white/70 transition-colors"
                    >
                      {preset.title}
                    </button>
                  ))}
                </div>
              </div>
            )}

            <form onSubmit={saveFavorite} className="space-y-3">
              <div>
                <label className="block text-[11px] text-white/50 mb-1">Name</label>
                <input
                  type="text"
                  required
                  value={favoriteTitle}
                  onChange={(e) => setFavoriteTitle(e.target.value)}
                  placeholder="e.g. GitHub"
                  className="w-full px-3 py-2 rounded-xl bg-black/50 border border-white/10 focus:border-[#d4ad66] outline-none text-[13px] text-white"
                />
              </div>

              <div>
                <label className="block text-[11px] text-white/50 mb-1">URL</label>
                <input
                  type="text"
                  required
                  value={favoriteUrl}
                  onChange={(e) => setFavoriteUrl(e.target.value)}
                  placeholder="https://example.com"
                  className="w-full px-3 py-2 rounded-xl bg-black/50 border border-white/10 focus:border-[#d4ad66] outline-none text-[13px] text-white font-mono text-[12px]"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-xl border border-white/10 text-[12px] text-white/70 hover:bg-white/5"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-xl bg-[#d4ad66] text-black font-semibold text-[12px] hover:bg-[#e2bd78]"
                >
                  {editingFavorite ? 'Save Changes' : 'Add Shortcut'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
