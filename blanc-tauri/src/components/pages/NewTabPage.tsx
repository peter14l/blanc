import React, { useState, useEffect } from 'react';
import {
  Search,
  ShieldCheck,
  Plus,
  MoreVertical,
  Trash2,
  Edit2,
  ExternalLink,
  History,
  Bookmark as BookmarkIcon,
  Settings,
  Sparkles,
  Globe,
  Lock,
  ArrowRight,
  X,
} from 'lucide-react';
import { Favorite, BrowserSettings, AdblockStats } from '../../types/browser';

interface NewTabPageProps {
  favorites: Favorite[];
  onNavigate: (url: string) => void;
  onAddFavorite: (fav: Omit<Favorite, 'id'>) => void;
  onRemoveFavorite: (id: string) => void;
  onUpdateFavorite: (id: string, updates: Partial<Favorite>) => void;
  settings: BrowserSettings;
  adblockStats: AdblockStats;
  currentTabBlockedTrackers?: number;
}

export const NewTabPage: React.FC<NewTabPageProps> = ({
  favorites,
  onNavigate,
  onAddFavorite,
  onRemoveFavorite,
  onUpdateFavorite,
  settings,
  adblockStats,
  currentTabBlockedTrackers = 0,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [timeStr, setTimeStr] = useState('');
  const [greeting, setGreeting] = useState('Welcome');
  
  // Add/Edit favorite modal state
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingFavorite, setEditingFavorite] = useState<Favorite | null>(null);
  const [favoriteTitle, setFavoriteTitle] = useState('');
  const [favoriteUrl, setFavoriteUrl] = useState('');
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  // Update clock & greeting
  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setTimeStr(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );

      const hour = now.getHours();
      if (hour >= 5 && hour < 12) {
        setGreeting('Good morning');
      } else if (hour >= 12 && hour < 18) {
        setGreeting('Good afternoon');
      } else if (hour >= 18 && hour < 22) {
        setGreeting('Good evening');
      } else {
        setGreeting('Good night');
      }
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

    // Use selected search engine
    let searchUrl = `https://duckduckgo.com/?q=${encodeURIComponent(q)}`;
    switch (settings.searchEngine) {
      case 'Google':
        searchUrl = `https://www.google.com/search?q=${encodeURIComponent(q)}`;
        break;
      case 'Bing':
        searchUrl = `https://www.bing.com/search?q=${encodeURIComponent(q)}`;
        break;
      case 'Ecosia':
        searchUrl = `https://www.ecosia.org/search?q=${encodeURIComponent(q)}`;
        break;
      case 'Kagi':
        searchUrl = `https://kagi.com/search?q=${encodeURIComponent(q)}`;
        break;
      case 'DuckDuckGo':
      default:
        searchUrl = `https://duckduckgo.com/?q=${encodeURIComponent(q)}`;
        break;
    }

    onNavigate(searchUrl);
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

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-10 flex flex-col items-center select-none font-ui">
      {/* HEADER SECTION: Sunrise Hero Mark, Greeting & Clock */}
      <div className="flex flex-col items-center text-center mt-4 sm:mt-8 mb-8 space-y-3 max-w-xl">
        {/* Sunrise Hero Mark */}
        <div className="relative group cursor-pointer" onClick={() => onNavigate('https://blancbrowser.com')}>
          <div className="w-20 h-20 rounded-2xl bg-gradient-to-b from-[#d4ad66]/20 to-[#805d28]/10 border border-[#d4ad66]/30 flex items-center justify-center shadow-[0_8px_30px_rgba(212,173,102,0.15)] transition-transform duration-300 group-hover:scale-105">
            <img
              src="/sunrise-hero-mark.png"
              alt="Blanc Sunrise Mark"
              className="w-14 h-14 object-contain filter drop-shadow-[0_2px_8px_rgba(212,173,102,0.4)]"
              onError={(e) => {
                // Fallback SVG if image not found
                e.currentTarget.style.display = 'none';
                e.currentTarget.nextElementSibling?.classList.remove('hidden');
              }}
            />
            <div className="hidden flex items-center justify-center text-[#d4ad66]">
              <Sparkles className="w-8 h-8" />
            </div>
          </div>
        </div>

        {/* Dynamic Greeting & Clock */}
        <div className="space-y-1">
          <div className="text-3xl sm:text-4xl font-light tracking-tight font-mono text-white/90">
            {timeStr || '12:00'}
          </div>
          <h1 className="text-lg sm:text-xl font-medium text-white/80">
            {greeting}, <span className="text-[#d4ad66] font-semibold">Blanc</span>
          </h1>
        </div>
      </div>

      {/* SEARCH BAR SECTION */}
      <form
        onSubmit={handleSearch}
        className="w-full max-w-xl relative flex items-center h-12 px-4 rounded-2xl bg-white/[0.06] hover:bg-white/[0.09] focus-within:bg-black/50 border border-white/10 focus-within:border-[#d4ad66]/60 shadow-[0_10px_30px_rgba(0,0,0,0.35)] transition-all duration-200 mb-10 group"
      >
        <div className="text-white/40 group-focus-within:text-[#d4ad66] mr-3 transition-colors">
          <Search className="w-4 h-4" />
        </div>
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder={`Search ${settings.searchEngine} or enter address...`}
          className="flex-1 bg-transparent text-[14px] text-white placeholder-white/30 outline-none border-none font-normal"
        />
        <div className="flex items-center space-x-1.5 ml-2">
          <span className="text-[11px] text-white/30 px-2 py-0.5 rounded-md bg-white/5 border border-white/5 hidden sm:inline-block">
            {settings.searchEngine}
          </span>
          {searchQuery.trim() && (
            <button
              type="submit"
              className="p-1.5 rounded-lg bg-[#d4ad66]/20 hover:bg-[#d4ad66]/30 text-[#d4ad66] transition-colors"
            >
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </form>

      {/* FAVORITES / PINNED SITES GRID */}
      <div className="w-full max-w-3xl mb-10">
        <div className="flex items-center justify-between mb-3 px-1">
          <div className="flex items-center space-x-2">
            <span className="text-[13px] font-semibold text-white/80">Favorites & Pinned</span>
            <span className="text-[11px] text-white/40 px-2 py-0.5 rounded-full bg-white/5">
              {favorites.length}
            </span>
          </div>
          <button
            onClick={openAddFavorite}
            className="flex items-center space-x-1 text-[12px] text-[#d4ad66] hover:text-[#e5be73] hover:underline transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>Add Shortcut</span>
          </button>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          {favorites.map((fav) => {
            const domain = fav.url.replace(/^https?:\/\//, '').split('/')[0];
            const initial = fav.title ? fav.title[0].toUpperCase() : 'W';

            return (
              <div
                key={fav.id}
                onClick={() => onNavigate(fav.url)}
                className="group relative flex flex-col items-center justify-center p-4 rounded-2xl bg-white/[0.04] hover:bg-white/[0.08] border border-white/5 hover:border-white/15 transition-all duration-150 cursor-pointer text-center"
              >
                {/* Menu button on hover */}
                <div
                  className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity"
                  onClick={(e) => e.stopPropagation()}
                >
                  <button
                    onClick={() => setActiveMenuId(activeMenuId === fav.id ? null : fav.id)}
                    className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
                  >
                    <MoreVertical className="w-3.5 h-3.5" />
                  </button>

                  {/* Dropdown Menu */}
                  {activeMenuId === fav.id && (
                    <div className="absolute right-0 top-6 w-32 bg-[#202020] border border-white/10 rounded-xl shadow-2xl py-1 z-30 animate-fade-in">
                      <button
                        onClick={() => openEditFavorite(fav)}
                        className="w-full flex items-center space-x-2 px-3 py-1.5 text-[12px] text-white/80 hover:bg-white/10 hover:text-white text-left"
                      >
                        <Edit2 className="w-3 h-3" />
                        <span>Edit</span>
                      </button>
                      <button
                        onClick={() => {
                          onRemoveFavorite(fav.id);
                          setActiveMenuId(null);
                        }}
                        className="w-full flex items-center space-x-2 px-3 py-1.5 text-[12px] text-red-400 hover:bg-red-500/10 text-left"
                      >
                        <Trash2 className="w-3 h-3" />
                        <span>Delete</span>
                      </button>
                    </div>
                  )}
                </div>

                {/* Favicon or Initial Icon */}
                <div className="w-11 h-11 rounded-xl bg-white/[0.07] border border-white/10 flex items-center justify-center mb-2.5 text-[15px] font-semibold text-[#d4ad66] group-hover:scale-105 group-hover:bg-[#d4ad66]/10 transition-all">
                  {fav.favicon ? (
                    <img src={fav.favicon} alt="" className="w-5 h-5 rounded" />
                  ) : (
                    <span>{initial}</span>
                  )}
                </div>

                <span className="text-[13px] font-medium text-white/90 truncate max-w-[120px] mb-0.5">
                  {fav.title}
                </span>
                <span className="text-[11px] text-white/40 truncate max-w-[120px]">
                  {domain}
                </span>
              </div>
            );
          })}

          {/* Add Favorite Card */}
          <button
            onClick={openAddFavorite}
            className="flex flex-col items-center justify-center p-4 rounded-2xl border border-dashed border-white/10 hover:border-[#d4ad66]/40 hover:bg-[#d4ad66]/5 transition-all text-white/40 hover:text-[#d4ad66] group cursor-pointer"
          >
            <div className="w-11 h-11 rounded-xl bg-white/[0.03] group-hover:bg-[#d4ad66]/10 border border-white/5 flex items-center justify-center mb-2.5 transition-colors">
              <Plus className="w-5 h-5" />
            </div>
            <span className="text-[13px] font-medium">Add Shortcut</span>
            <span className="text-[11px] opacity-60">Custom site</span>
          </button>
        </div>
      </div>

      {/* PRIVACY SHIELD & SYSTEM STATUS WIDGET */}
      <div className="w-full max-w-3xl grid grid-cols-1 sm:grid-cols-2 gap-4 mb-10">
        {/* Adblock / Protection Widget */}
        <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex items-start space-x-3.5">
          <div className="p-2.5 rounded-xl bg-[#d4ad66]/15 border border-[#d4ad66]/30 text-[#d4ad66] shrink-0 mt-0.5">
            <ShieldCheck className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center space-x-2">
              <span className="text-[13px] font-semibold text-white/90">Blanc Blocker</span>
              <span className="px-1.5 py-0.5 rounded text-[10px] uppercase font-bold tracking-wider bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                Active
              </span>
            </div>
            <p className="text-[12px] text-white/50 mt-1">
              Network-layer telemetry, ad scripts, and third-party trackers are blocked at source.
            </p>
            <div className="flex items-center space-x-4 mt-2.5 pt-2 border-t border-white/5 text-[12px]">
              <div>
                <span className="text-white/40">Tab Blocked: </span>
                <span className="font-semibold text-[#d4ad66]">{currentTabBlockedTrackers}</span>
              </div>
              <div>
                <span className="text-white/40">All-time: </span>
                <span className="font-semibold text-white/90">
                  {adblockStats.totalBlocked.toLocaleString()}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Security & Private Architecture Widget */}
        <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/5 flex items-start space-x-3.5">
          <div className="p-2.5 rounded-xl bg-blue-500/15 border border-blue-500/30 text-blue-400 shrink-0 mt-0.5">
            <Lock className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex items-center space-x-2">
              <span className="text-[13px] font-semibold text-white/90">Zero Tracking</span>
              <span className="px-1.5 py-0.5 rounded text-[10px] uppercase font-bold tracking-wider bg-blue-500/20 text-blue-400 border border-blue-500/30">
                Private
              </span>
            </div>
            <p className="text-[12px] text-white/50 mt-1">
              No telemetry, no tracking IDs, and no third-party cloud data synching. Your browsing stays yours.
            </p>
            <div className="flex items-center space-x-3 mt-2.5 pt-2 border-t border-white/5 text-[12px]">
              <span className="text-white/40">Local Storage:</span>
              <span className="text-emerald-400 font-mono text-[11px]">Sandboxed & Encrypted</span>
            </div>
          </div>
        </div>
      </div>

      {/* QUICK LINKS FOOTER */}
      <div className="flex flex-wrap items-center justify-center gap-3 sm:gap-6 text-[12px] text-white/50 mt-auto pt-4">
        <button
          onClick={() => onNavigate('blanc://history')}
          className="flex items-center space-x-1.5 hover:text-white transition-colors"
        >
          <History className="w-3.5 h-3.5" />
          <span>History</span>
        </button>
        <span className="text-white/20">•</span>
        <button
          onClick={() => onNavigate('blanc://bookmarks')}
          className="flex items-center space-x-1.5 hover:text-white transition-colors"
        >
          <BookmarkIcon className="w-3.5 h-3.5" />
          <span>Bookmarks</span>
        </button>
        <span className="text-white/20">•</span>
        <button
          onClick={() => onNavigate('blanc://settings')}
          className="flex items-center space-x-1.5 hover:text-white transition-colors"
        >
          <Settings className="w-3.5 h-3.5" />
          <span>Settings</span>
        </button>
        <span className="text-white/20">•</span>
        <button
          onClick={() => onNavigate('https://github.com/bnfy/blanc')}
          className="flex items-center space-x-1.5 hover:text-white transition-colors"
        >
          <Globe className="w-3.5 h-3.5" />
          <span>GitHub</span>
          <ExternalLink className="w-2.5 h-2.5 opacity-60" />
        </button>
      </div>

      {/* ADD / EDIT FAVORITE MODAL */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-md bg-[#1c1c1c] border border-white/10 rounded-2xl shadow-2xl p-5 animate-slide-down">
            <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
              <h2 className="text-[15px] font-semibold text-white/90">
                {editingFavorite ? 'Edit Shortcut' : 'Add Shortcut'}
              </h2>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={saveFavorite} className="space-y-4">
              <div>
                <label className="block text-[12px] font-medium text-white/70 mb-1">
                  Name / Title
                </label>
                <input
                  type="text"
                  required
                  value={favoriteTitle}
                  onChange={(e) => setFavoriteTitle(e.target.value)}
                  placeholder="e.g. GitHub"
                  className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60"
                />
              </div>

              <div>
                <label className="block text-[12px] font-medium text-white/70 mb-1">
                  Web Address (URL)
                </label>
                <input
                  type="text"
                  required
                  value={favoriteUrl}
                  onChange={(e) => setFavoriteUrl(e.target.value)}
                  placeholder="https://example.com"
                  className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60 font-mono"
                />
              </div>

              <div className="flex items-center justify-end space-x-2 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-xl text-white/60 hover:text-white hover:bg-white/10 text-[12px]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] text-[#12100b] font-medium text-[12px] transition-colors"
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
