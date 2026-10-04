import React, { useState, useEffect } from 'react';
import {
  Search,
  ShieldCheck,
  Plus,
  MoreVertical,
  Trash2,
  Edit2,
  Sparkles,
  ArrowRight,
  X,
  Clock,
  Bookmark,
  History,
  Settings,
} from 'lucide-react';
import { Favorite, BrowserSettings, AdblockStats, HistoryEntry, Bookmark as BookmarkType } from '../../types/browser';
import { useSearchSuggestions, SuggestionItem } from '../../hooks/useSearchSuggestions';
import { SuggestionPicker } from '../SuggestionPicker';

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
  currentTabBlockedTrackers = 0,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [activeEngine, setActiveEngine] = useState<string>(settings.searchEngine || 'Google');
  const [timeStr, setTimeStr] = useState('');
  const [dateStr, setDateStr] = useState('');
  const [greeting, setGreeting] = useState('Welcome');

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

  // Update clock, date & greeting
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );
      setDateStr(
        now.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
      );

      const hour = now.getHours();
      if (hour >= 5 && hour < 12) setGreeting('Good morning');
      else if (hour >= 12 && hour < 17) setGreeting('Good afternoon');
      else if (hour >= 17 && hour < 22) setGreeting('Good evening');
      else setGreeting('Good night');
    };

    updateTime();
    const interval = setInterval(updateTime, 1000);
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
    <div className="w-full h-full overflow-y-auto bg-[#0a0a0a] text-white flex flex-col items-center select-none font-ui relative">
      {/* AMBIENT BACKGROUND GLOW */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[950px] h-[450px] bg-gradient-to-b from-[#d4ad66]/12 via-[#d4ad66]/[0.02] to-transparent rounded-full blur-3xl pointer-events-none -z-0" />

      <div className="w-full max-w-5xl lg:max-w-6xl px-6 sm:px-12 md:px-16 py-12 sm:py-20 flex flex-col items-center z-10 my-auto space-y-10 sm:space-y-14">
        {/* TOP BRAND EMBLEM & TIME */}
        <div className="flex flex-col items-center text-center space-y-4">
          <div
            onClick={() => onNavigate('https://blancbrowser.com')}
            className="group relative cursor-pointer flex flex-col items-center"
            title="Blanc Browser"
          >
            <div className="relative w-20 h-20 sm:w-24 sm:h-24 rounded-3xl bg-gradient-to-b from-[#d4ad66]/25 to-[#805d28]/10 border border-[#d4ad66]/40 flex items-center justify-center shadow-[0_10px_40px_rgba(212,173,102,0.25)] group-hover:shadow-[0_16px_55px_rgba(212,173,102,0.4)] group-hover:scale-105 transition-all duration-300">
              <img
                src="/sunrise-hero-mark.png"
                alt="Blanc"
                className="w-13 h-13 sm:w-16 sm:h-16 object-contain filter drop-shadow-[0_4px_14px_rgba(212,173,102,0.5)]"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                  e.currentTarget.nextElementSibling?.classList.remove('hidden');
                }}
              />
              <div className="hidden text-[#d4ad66]">
                <Sparkles className="w-9 h-9" />
              </div>
            </div>
          </div>

          {/* CLOCK & GREETING */}
          <div className="space-y-2">
            <div className="text-6xl sm:text-7xl md:text-8xl font-extralight tracking-tight font-mono text-white/95 flex items-center justify-center space-x-2">
              <span>{timeStr || '12:00'}</span>
            </div>
            <div className="flex items-center justify-center space-x-2.5 text-[14px] sm:text-[15px] text-white/60 font-medium">
              <span>{greeting}, <span className="text-[#d4ad66] font-semibold">Explorer</span></span>
              <span>•</span>
              <span className="font-serif italic text-white/40">{dateStr}</span>
            </div>
          </div>

          {/* QUICK HUB NAVIGATION PILLS */}
          <div className="flex items-center justify-center gap-2 pt-1 flex-wrap">
            <button
              onClick={() => onNavigate('blanc://bookmarks')}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.09] border border-white/10 hover:border-[#d4ad66]/40 text-white/75 hover:text-white text-[12px] transition-all cursor-pointer active:scale-95"
            >
              <Bookmark className="w-3.5 h-3.5 text-[#d4ad66]" />
              <span className="font-medium">Bookmarks</span>
            </button>
            <button
              onClick={() => onNavigate('blanc://history')}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.09] border border-white/10 hover:border-sky-400/40 text-white/75 hover:text-white text-[12px] transition-all cursor-pointer active:scale-95"
            >
              <History className="w-3.5 h-3.5 text-sky-400" />
              <span className="font-medium">History</span>
            </button>
            <button
              onClick={() => onNavigate('blanc://settings')}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-full bg-white/[0.04] hover:bg-white/[0.09] border border-white/10 hover:border-purple-400/40 text-white/75 hover:text-white text-[12px] transition-all cursor-pointer active:scale-95"
            >
              <Settings className="w-3.5 h-3.5 text-purple-400" />
              <span className="font-medium">Settings</span>
            </button>
          </div>
        </div>

        {/* UNIFIED SEARCH & COMMAND CAPSULE */}
        <div className="w-full max-w-3xl lg:max-w-4xl">
          <form
            onSubmit={handleSearch}
            className="relative flex items-center h-15 sm:h-16 px-5 sm:px-6 rounded-2xl sm:rounded-3xl bg-[#141414]/90 hover:bg-[#181818] focus-within:bg-[#111111] border border-white/10 focus-within:border-[#d4ad66]/60 shadow-[0_16px_45px_rgba(0,0,0,0.55)] transition-all duration-200 group"
          >
            <div className="text-white/40 group-focus-within:text-[#d4ad66] mr-4 transition-colors shrink-0">
              <Search className="w-5 h-5 sm:w-6 sm:h-6" />
            </div>

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
              placeholder={`Search ${activeEngine} or enter a web address...`}
              className="flex-1 bg-transparent text-[15px] sm:text-[17px] text-white placeholder-white/30 outline-none border-none font-normal"
            />

            {/* Clear or Submit */}
            <div className="flex items-center space-x-2 ml-3 shrink-0">
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
              <button
                type="submit"
                className="px-3.5 py-2.5 rounded-xl bg-[#d4ad66] text-black hover:bg-[#e2bd78] active:scale-95 transition-all shadow-md font-bold"
                title="Search"
              >
                <ArrowRight className="w-4 h-4 stroke-[2.5]" />
              </button>
            </div>

            {/* SMART AUTOCOMPLETE / SUGGESTION PICKER */}
            <SuggestionPicker
              suggestions={suggestions}
              selectedIndex={selectedIndex}
              onSelect={handleSelectSuggestion}
              onHoverIndex={setSelectedIndex}
              searchEngine={activeEngine}
              visible={isPickerVisible && isFocused}
            />
          </form>

          {/* ENGINE SWITCHER PILLS */}
          <div className="flex items-center justify-center space-x-2 mt-3.5 overflow-x-auto py-1 text-[12px]">
            <span className="text-white/30 mr-1.5 text-[11px] uppercase font-semibold tracking-wider">Engine:</span>
            {SEARCH_ENGINES.map((eng) => (
              <button
                key={eng.id}
                type="button"
                onClick={() => setActiveEngine(eng.id)}
                className={`px-3 py-1 rounded-full border transition-all ${
                  activeEngine.toLowerCase() === eng.id.toLowerCase()
                    ? 'bg-[#d4ad66]/20 border-[#d4ad66]/50 text-[#d4ad66] font-medium'
                    : 'bg-white/[0.03] border-white/5 text-white/40 hover:text-white/80 hover:bg-white/[0.06]'
                }`}
              >
                {eng.label}
              </button>
            ))}
          </div>
        </div>

        {/* PINNED FAVORITES GRID */}
        <div className="w-full max-w-3xl lg:max-w-4xl">
          <div className="flex items-center justify-between mb-4 px-1">
            <div className="flex items-center space-x-2.5">
              <span className="text-[14px] sm:text-[15px] font-semibold text-white/85">Pinned Sites</span>
              <span className="text-[11px] sm:text-[12px] text-white/40 px-2.5 py-0.5 rounded-full bg-white/5">
                {favorites.length}
              </span>
            </div>
            <button
              onClick={openAddFavorite}
              className="flex items-center space-x-1.5 text-[13px] text-[#d4ad66] hover:text-[#e5be73] transition-colors"
            >
              <Plus className="w-4 h-4" />
              <span>Add Shortcut</span>
            </button>
          </div>

          <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-3.5 sm:gap-4.5">
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
                  className="group relative flex flex-col items-center justify-center p-4 sm:p-5 rounded-2xl sm:rounded-3xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/5 hover:border-white/15 transition-all duration-200 cursor-pointer text-center hover:-translate-y-0.5"
                >
                  {/* Context menu trigger */}
                  <div
                    className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <button
                      onClick={() => setActiveMenuId(activeMenuId === fav.id ? null : fav.id)}
                      className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10"
                    >
                      <MoreVertical className="w-3.5 h-3.5" />
                    </button>

                    {activeMenuId === fav.id && (
                      <div className="absolute right-0 top-6 w-32 bg-[#1e1e1e] border border-white/10 rounded-xl shadow-2xl py-1.5 z-30">
                        <button
                          onClick={() => openEditFavorite(fav)}
                          className="w-full flex items-center space-x-2 px-3 py-1.5 text-[12px] text-white/80 hover:bg-white/10 text-left"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                          <span>Edit</span>
                        </button>
                        <button
                          onClick={() => {
                            onRemoveFavorite(fav.id);
                            setActiveMenuId(null);
                          }}
                          className="w-full flex items-center space-x-2 px-3 py-1.5 text-[12px] text-red-400 hover:bg-red-500/10 text-left"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Delete</span>
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Favicon Tile */}
                  <div className="w-13 h-13 sm:w-14 sm:h-14 rounded-2xl bg-white/[0.05] border border-white/10 flex items-center justify-center mb-2.5 group-hover:scale-105 group-hover:border-[#d4ad66]/40 transition-all overflow-hidden p-3">
                    <img
                      src={faviconSrc}
                      alt=""
                      className="w-6 h-6 sm:w-7 sm:h-7 object-contain"
                      onError={(e) => {
                        e.currentTarget.style.display = 'none';
                        e.currentTarget.nextElementSibling?.classList.remove('hidden');
                      }}
                    />
                    <div className="hidden font-mono font-bold text-[#d4ad66] text-[15px]">
                      {fav.title[0]?.toUpperCase() || 'W'}
                    </div>
                  </div>

                  <span className="text-[12px] sm:text-[13px] font-medium text-white/90 truncate w-full px-1">
                    {fav.title}
                  </span>
                  <span className="text-[10px] sm:text-[11px] text-white/40 truncate w-full px-1 mt-0.5">
                    {domain}
                  </span>
                </div>
              );
            })}

            {/* Quick Add Button */}
            <button
              onClick={openAddFavorite}
              className="flex flex-col items-center justify-center p-4 sm:p-5 rounded-2xl sm:rounded-3xl border border-dashed border-white/10 hover:border-[#d4ad66]/40 hover:bg-[#d4ad66]/5 transition-all text-white/40 hover:text-[#d4ad66] group cursor-pointer hover:-translate-y-0.5"
            >
              <div className="w-13 h-13 sm:w-14 sm:h-14 rounded-2xl bg-white/[0.02] group-hover:bg-[#d4ad66]/10 border border-white/5 flex items-center justify-center mb-2.5 transition-colors">
                <Plus className="w-5 h-5" />
              </div>
              <span className="text-[12px] sm:text-[13px] font-medium">Add New</span>
              <span className="text-[10px] sm:text-[11px] opacity-40 mt-0.5">Shortcut</span>
            </button>
          </div>
        </div>

        {/* PICK UP WHERE YOU LEFT OFF (Recent Activity) */}
        {recentHistory.length > 0 && (
          <div className="w-full max-w-3xl lg:max-w-4xl">
            <div className="flex items-center justify-between mb-3 px-1">
              <div className="flex items-center space-x-2.5">
                <Clock className="w-4 h-4 text-[#d4ad66]" />
                <span className="text-[13px] sm:text-[14px] font-semibold text-white/80">Pick up where you left off</span>
              </div>
              <button
                onClick={() => onNavigate('blanc://history')}
                className="text-[12px] text-white/40 hover:text-white transition-colors"
              >
                View all history →
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-3.5">
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
                    className="flex items-center space-x-3.5 p-3 sm:p-3.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.06] border border-white/5 hover:border-white/10 transition-colors cursor-pointer text-left"
                  >
                    <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-white/5 flex items-center justify-center text-[#d4ad66] shrink-0 text-[12px] sm:text-[13px] font-bold">
                      {item.title?.[0]?.toUpperCase() || 'W'}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-[13px] font-medium text-white/90 truncate">{item.title}</p>
                      <p className="text-[11px] text-white/40 truncate">{domain}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* PROTECTION HUD / TELEMETRY TICKER */}
        <div className="w-full max-w-3xl lg:max-w-4xl p-5 sm:p-6 rounded-2xl sm:rounded-3xl bg-gradient-to-r from-white/[0.04] to-white/[0.01] border border-white/5 flex flex-col sm:flex-row items-center justify-between gap-5">
          <div className="flex items-center space-x-4">
            <div className="p-3 sm:p-3.5 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 shrink-0">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-[14px] sm:text-[15px] font-semibold text-white/90">Blanc Shield Protection</span>
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
              </div>
              <p className="text-[12px] sm:text-[13px] text-white/50 mt-1">
                Independent network-layer filter • Zero ad trackers or third-party telemetries.
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-5 text-[13px] shrink-0">
            {currentTabBlockedTrackers > 0 && (
              <>
                <div className="text-center sm:text-right">
                  <p className="text-white/40 text-[10px] sm:text-[11px] uppercase font-semibold">This Tab</p>
                  <p className="font-mono font-bold text-emerald-400 text-[16px] sm:text-[18px]">
                    {currentTabBlockedTrackers}
                  </p>
                </div>
                <div className="h-7 w-[1px] bg-white/10" />
              </>
            )}
            <div className="text-center sm:text-right">
              <p className="text-white/40 text-[10px] sm:text-[11px] uppercase font-semibold">Trackers Blocked</p>
              <p className="font-mono font-bold text-[#d4ad66] text-[16px] sm:text-[18px]">
                {adblockStats.totalBlocked.toLocaleString()}
              </p>
            </div>
            <div className="h-7 w-[1px] bg-white/10" />
            <div className="text-center sm:text-right">
              <p className="text-white/40 text-[10px] sm:text-[11px] uppercase font-semibold">Data Saved</p>
              <p className="font-mono font-bold text-white/90 text-[16px] sm:text-[18px]">
                ~{(adblockStats.totalBlocked * 0.08).toFixed(1)} MB
              </p>
            </div>
          </div>
        </div>

        {/* FOOTER KEYBOARD SHORTCUTS HINTS */}
        <div className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 text-[12px] sm:text-[13px] text-white/30 pt-2 font-mono">
          <span><span className="text-white/60">⌘K</span> Quick Switcher</span>
          <span>•</span>
          <span><span className="text-white/60">⌘T</span> New Tab</span>
          <span>•</span>
          <span><span className="text-white/60">⌘W</span> Close Tab</span>
          <span>•</span>
          <span><span className="text-white/60">⌘,</span> Settings</span>
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
