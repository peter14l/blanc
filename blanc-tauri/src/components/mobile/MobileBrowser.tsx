import React, { useState, useRef, useEffect, useCallback } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  RotateCw,
  Plus,
  Search,
  ShieldCheck,
  MoreVertical,
  X,
  Bookmark,
  History,
  Settings,
  Globe,
  ExternalLink,
  Loader2,
} from 'lucide-react';
import { Tab, BrowserSettings, AdblockStats, HistoryEntry, Bookmark as BookmarkType, Favorite } from '../../types/browser';
import { useWebPageLoader } from '../../hooks/useWebPageLoader';
import { NewTabPage } from '../pages/NewTabPage';
import { HistoryPage } from '../pages/HistoryPage';
import { SettingsPage } from '../pages/SettingsPage';
import { BookmarksPage } from '../pages/BookmarksPage';

interface MobileBrowserProps {
  tabs: Tab[];
  activeTab: Tab | null;
  settings: BrowserSettings;
  adblockStats: AdblockStats;
  history: HistoryEntry[];
  bookmarks: BookmarkType[];
  favorites: Favorite[];
  onNavigate: (url: string) => void;
  onCreateTab: (url?: string) => void;
  onCloseTab: (id: string) => void;
  onCloseAllTabs: () => void;
  onSwitchTab: (id: string) => void;
  onUpdateSettings: (settings: Partial<BrowserSettings>) => void;
  onAddBookmark: (bookmark: Omit<BookmarkType, 'id' | 'createdAt'>) => void;
  onRemoveBookmark: (id: string) => void;
  onUpdateBookmark: (id: string, updates: Partial<BookmarkType>) => void;
  onAddFavorite: (fav: Omit<Favorite, 'id'>) => void;
  onRemoveFavorite: (id: string) => void;
  onUpdateFavorite: (id: string, updates: Partial<Favorite>) => void;
  onRemoveHistoryEntry: (id: string) => void;
  onClearHistory: () => void;
}

export const MobileBrowser: React.FC<MobileBrowserProps> = ({
  tabs,
  activeTab,
  settings,
  adblockStats,
  history,
  bookmarks,
  favorites,
  onNavigate,
  onCreateTab,
  onCloseTab,
  onCloseAllTabs,
  onSwitchTab,
  onUpdateSettings,
  onAddBookmark,
  onRemoveBookmark,
  onUpdateBookmark,
  onAddFavorite,
  onRemoveFavorite,
  onUpdateFavorite,
  onRemoveHistoryEntry,
  onClearHistory,
}) => {
  const [isEditingUrl, setIsEditingUrl] = useState(false);
  const [urlInput, setUrlInput] = useState('');
  const [isTabsOpen, setIsTabsOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const activeUrl = activeTab?.url || 'blanc://newtab';
  const isNewTab = activeUrl === 'blanc://newtab' || activeUrl === 'about:blank' || !activeUrl;
  const isHistory = activeUrl === 'blanc://history';
  const isSettings = activeUrl === 'blanc://settings';
  const isBookmarks = activeUrl === 'blanc://bookmarks';
  const isInternal = isNewTab || isHistory || isSettings || isBookmarks;

  const { content, loading: pageLoading, isDirect, directSrc } = useWebPageLoader(activeUrl);

  // Listen for navigation messages from iframes (YouTube hub, search results, etc.)
  useEffect(() => {
    const handleMessage = (e: MessageEvent) => {
      if (e.data && e.data.type === 'BLANC_NAV' && typeof e.data.url === 'string') {
        onNavigate(e.data.url);
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [onNavigate]);

  // Sync URL input when active tab changes
  useEffect(() => {
    if (!isEditingUrl && activeTab) {
      setUrlInput(activeTab.url === 'blanc://newtab' ? '' : activeTab.url);
    }
  }, [activeTab, isEditingUrl]);

  // Focus URL input when editing
  useEffect(() => {
    if (isEditingUrl) {
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [isEditingUrl]);

  // Handle URL form submission
  const handleSubmitUrl = useCallback((e: React.FormEvent) => {
    e.preventDefault();
    if (urlInput.trim()) {
      onNavigate(urlInput.trim());
      setIsEditingUrl(false);
    }
  }, [urlInput, onNavigate]);

  // Get clean domain for display
  const getCleanDomain = (url: string) => {
    if (url.startsWith('blanc://')) {
      return url.replace('blanc://', '').toUpperCase();
    }
    try {
      return new URL(url).hostname.replace(/^www\./, '');
    } catch {
      return url;
    }
  };

  // Mobile-specific navigation handlers that work with iframe + React state
  const handleGoBack = useCallback(() => {
    if (!activeTab) return;
    
    // Use the tab's history array maintained by useBrowserIPC
    const hist = (activeTab as any).history || [];
    const idx = (activeTab as any).historyIndex ?? 0;
    
    if (idx > 0) {
      const nextIdx = idx - 1;
      const targetUrl = hist[nextIdx];
      
      // Navigate via React state (updates tab URL, triggers iframe reload)
      onNavigate(targetUrl);
    }
    // Also try iframe history.back() for direct iframes as fallback
    else if (iframeRef.current && isDirect) {
      try {
        iframeRef.current.contentWindow?.history.back();
      } catch (e) {
        // Cross-origin iframe - can't access history
      }
    }
  }, [activeTab, isDirect, onNavigate]);

  const handleGoForward = useCallback(() => {
    if (!activeTab) return;
    
    const hist = (activeTab as any).history || [];
    const idx = (activeTab as any).historyIndex ?? 0;
    
    if (idx < hist.length - 1) {
      const nextIdx = idx + 1;
      const targetUrl = hist[nextIdx];
      
      onNavigate(targetUrl);
    }
    else if (iframeRef.current && isDirect) {
      try {
        iframeRef.current.contentWindow?.history.forward();
      } catch (e) {
        // Cross-origin iframe - can't access history
      }
    }
  }, [activeTab, isDirect, onNavigate]);

  const handleReload = useCallback(() => {
    if (iframeRef.current) {
      if (isDirect) {
        iframeRef.current.src = iframeRef.current.src; // Force reload
      } else {
        iframeRef.current.contentWindow?.location.reload();
      }
    }
    // No fallback needed - iframe ref handles reload
  }, [isDirect]);

  // Determine if back/forward should be enabled
  const canGoBack = activeTab 
    ? ((activeTab as any).historyIndex ?? 0) > 0 || (isDirect && iframeRef.current)
    : false;
  
  const canGoForward = activeTab
    ? ((activeTab as any).historyIndex ?? 0) < (((activeTab as any).history || []).length - 1) || (isDirect && iframeRef.current)
    : false;

  return (
    <div className="fixed inset-0 w-full h-full bg-[#0a0a0a] text-white flex flex-col overflow-hidden select-none touch-manipulation">
      {/* MOBILE TOP APP BAR */}
      <header className="w-full shrink-0 bg-[#121212]/95 backdrop-blur-md border-b border-white/10 z-30 pt-safe">
        <div className="h-14 px-3 flex items-center justify-between space-x-2">
          {/* URL Pill / Search Trigger */}
          <div className="flex-1 min-w-0">
            {isEditingUrl ? (
              <form onSubmit={handleSubmitUrl} className="relative w-full flex items-center">
                <input
                  ref={inputRef}
                  type="text"
                  value={urlInput}
                  onChange={(e) => setUrlInput(e.target.value)}
                  onBlur={() => setIsEditingUrl(false)}
                  placeholder="Search or enter website name..."
                  className="w-full h-10 px-3.5 pr-9 bg-[#1f1f1f] text-white text-[15px] rounded-xl border border-[#d4ad66]/50 outline-none focus:ring-1 focus:ring-[#d4ad66]"
                />
                {urlInput && (
                  <button
                    type="button"
                    onMouseDown={(e) => {
                      e.preventDefault();
                      setUrlInput('');
                    }}
                    className="absolute right-2.5 p-1 text-white/50 hover:text-white"
                  >
                    <X className="w-4 h-4" />
                  </button>
                )}
              </form>
            ) : (
              <button
                onClick={() => setIsEditingUrl(true)}
                className="w-full h-10 px-3.5 bg-white/[0.06] hover:bg-white/[0.09] active:bg-white/[0.12] rounded-xl border border-white/10 flex items-center justify-between text-left transition-colors"
              >
                <div className="flex items-center space-x-2 min-w-0">
                  {isInternal ? (
                    <Globe className="w-4 h-4 text-[#d4ad66] shrink-0" />
                  ) : (
                    <ShieldCheck className="w-4 h-4 text-emerald-400 shrink-0" />
                  )}
                  <span className="text-[14px] font-medium text-white/90 truncate">
                    {isNewTab ? 'Search or type URL' : getCleanDomain(activeUrl)}
                  </span>
                </div>
                <Search className="w-3.5 h-3.5 text-white/40 shrink-0" />
              </button>
            )}
          </div>

          {/* Top Actions: Bookmarks, History, Reload & Menu */}
          <div className="flex items-center space-x-0.5 shrink-0">
            <button
              onClick={() => onNavigate('blanc://bookmarks')}
              title="Bookmarks"
              className="p-2 rounded-xl text-white/70 active:text-white active:bg-white/10"
            >
              <Bookmark className="w-4 h-4" />
            </button>
            <button
              onClick={() => onNavigate('blanc://history')}
              title="History"
              className="p-2 rounded-xl text-white/70 active:text-white active:bg-white/10"
            >
              <History className="w-4 h-4" />
            </button>
            <button
              onClick={handleReload}
              title="Reload Page"
              className="p-2 rounded-xl text-white/70 active:text-white active:bg-white/10"
            >
              <RotateCw className="w-4 h-4" />
            </button>
            <button
              onClick={() => setIsMenuOpen(true)}
              title="Menu"
              className="p-2 rounded-xl text-white/70 active:text-white active:bg-white/10"
            >
              <MoreVertical className="w-5 h-5" />
            </button>
          </div>
        </div>
      </header>

      {/* FULL-BLEED MOBILE VIEWPORT (No floating desktop cards or black margins) */}
      <main className="flex-1 w-full h-full relative overflow-hidden bg-[#0d0d0d] pb-24">
        {/* Internal: New Tab Page */}
        {isNewTab && (
          <div className="w-full h-full overflow-y-auto">
            <NewTabPage
              favorites={favorites}
              history={history}
              bookmarks={bookmarks}
              onNavigate={(url) => onNavigate(url)}
              onAddFavorite={onAddFavorite}
              onRemoveFavorite={onRemoveFavorite}
              onUpdateFavorite={onUpdateFavorite}
              settings={settings}
              adblockStats={adblockStats}
              currentTabBlockedTrackers={activeTab?.blocked_trackers || 0}
            />
          </div>
        )}

        {/* Internal: History */}
        {isHistory && (
          <div className="w-full h-full overflow-y-auto">
            <HistoryPage
              history={history}
              onNavigate={(url) => onNavigate(url)}
              onOpenNewTab={(url) => onCreateTab(url)}
              onRemoveEntry={onRemoveHistoryEntry}
              onClearHistory={onClearHistory}
            />
          </div>
        )}

        {/* Internal: Settings */}
        {isSettings && (
          <div className="w-full h-full overflow-y-auto">
            <SettingsPage
              settings={settings}
              onUpdateSettings={onUpdateSettings}
              onNavigate={(url) => onNavigate(url)}
            />
          </div>
        )}

        {/* Internal: Bookmarks */}
        {isBookmarks && (
          <div className="w-full h-full overflow-y-auto">
            <BookmarksPage
              bookmarks={bookmarks}
              onNavigate={(url) => onNavigate(url)}
              onOpenNewTab={(url) => onCreateTab(url)}
              onAddBookmark={onAddBookmark}
              onRemoveBookmark={onRemoveBookmark}
              onUpdateBookmark={onUpdateBookmark}
            />
          </div>
        )}

        {/* External Web Page Viewport: Full screen interactive web view */}
        {!isInternal && (
          <div className="w-full h-full relative bg-[#0d0d0d] flex flex-col">
            {/* Top progress bar while fetching */}
            {pageLoading && (
              <div className="absolute top-0 left-0 right-0 h-1 bg-[#d4ad66]/30 z-20 overflow-hidden">
                <div className="w-full h-full bg-[#d4ad66] animate-pulse origin-left" />
              </div>
            )}

            {/* Quick action floating pill for external system browser */}
            <div className="absolute top-2 right-2 z-10 flex items-center space-x-1.5 bg-black/75 backdrop-blur-md px-2.5 py-1 rounded-full border border-white/10 text-white/80 text-[11px] shadow-lg pointer-events-auto">
              <span className="truncate max-w-[120px] font-mono text-[11px] text-white/90">{getCleanDomain(activeUrl)}</span>
              <button
                onClick={() => window.open(activeUrl, '_blank')}
                title="Open in System Browser"
                className="hover:text-white p-0.5 active:scale-95 text-[#d4ad66]"
              >
                <ExternalLink className="w-3.5 h-3.5" />
              </button>
            </div>

            {pageLoading && !content && !isDirect ? (
              <div className="flex-1 w-full h-full flex flex-col items-center justify-center bg-[#0d0d0d] text-white p-6">
                <Loader2 className="w-8 h-8 text-[#d4ad66] animate-spin mb-3" />
                <p className="text-[15px] font-medium text-white/90">Loading {getCleanDomain(activeUrl)}...</p>
                <p className="text-[12px] text-white/40 mt-1 max-w-xs text-center truncate">{activeUrl}</p>
              </div>
            ) : content ? (
              <iframe
                ref={iframeRef}
                key={'content-' + activeTab?.id + '-' + activeTab?.url}
                srcDoc={content}
                title={activeTab?.title || 'Web Page'}
                className="w-full h-full border-none flex-1"
                sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals allow-downloads"
              />
            ) : (
              <iframe
                ref={iframeRef}
                key={'direct-' + activeTab?.id + '-' + directSrc}
                src={directSrc}
                title={activeTab?.title || 'Web Page'}
                className="w-full h-full border-none flex-1"
                sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals allow-downloads"
              />
            )}
          </div>
        )}
      </main>

      {/* MOBILE BOTTOM NAVIGATION BAR (Floating pill) */}
      <footer className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 pb-safe">
        <div className="border border-white/10 rounded-full px-5 py-3 flex items-center justify-center space-x-2 shadow-2xl">
          <button
            onClick={handleGoBack}
            disabled={!canGoBack}
            className="p-3 text-white/70 active:text-white disabled:opacity-25"
            title="Back"
          >
            <ChevronLeft className="w-7 h-7" />
          </button>

          <button
            onClick={handleGoForward}
            disabled={!canGoForward}
            className="p-3 text-white/70 active:text-white disabled:opacity-25"
            title="Forward"
          >
            <ChevronRight className="w-7 h-7" />
          </button>

          <button
            onClick={() => onCreateTab('blanc://newtab')}
            className="p-3 bg-[#d4ad66] hover:bg-[#c29c55] active:scale-95 text-black rounded-full shadow-lg transition-transform"
            title="New Tab"
          >
            <Plus className="w-6 h-6 stroke-[2.5]" />
          </button>

          <button
            onClick={() => setIsTabsOpen(true)}
            className="relative p-3 text-white/70 active:text-white"
            title="Tabs"
          >
            <div className="w-7 h-7 border-2 border-current rounded-md flex items-center justify-center font-mono text-[12px] font-bold">
              {tabs.length}
            </div>
          </button>

          <button
            onClick={() => onNavigate('blanc://newtab')}
            className="p-3 text-white/70 active:text-white"
            title="Home"
          >
            <Globe className="w-7 h-7" />
          </button>
        </div>
      </footer>

      {/* MOBILE TAB SWITCHER SHEET */}
      {isTabsOpen && (
        <div className="fixed inset-0 z-50 bg-black/90 backdrop-blur-xl flex flex-col animate-in fade-in duration-200">
          <div className="h-14 px-4 border-b border-white/10 flex items-center justify-between shrink-0">
            <span className="font-semibold text-[17px] text-white/90">
              Tabs ({tabs.length})
            </span>
            <div className="flex items-center space-x-3">
              <button
                onClick={() => {
                  onCloseAllTabs();
                  setIsTabsOpen(false);
                }}
                className="text-[13px] text-red-400 hover:text-red-300"
              >
                Close All
              </button>
              <button
                onClick={() => setIsTabsOpen(false)}
                className="p-1.5 rounded-full bg-white/10 text-white"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-4 grid grid-cols-1 sm:grid-cols-2 gap-3.5">
            {tabs.map((tab) => {
              const isCurrent = tab.id === activeTab?.id;
              return (
                <div
                  key={tab.id}
                  onClick={() => {
                    onSwitchTab(tab.id);
                    setIsTabsOpen(false);
                  }}
                  className={`p-3.5 rounded-2xl border transition-all flex flex-col justify-between h-28 cursor-pointer ${
                    isCurrent
                      ? 'bg-[#d4ad66]/15 border-[#d4ad66]/60 shadow-[0_0_20px_rgba(212,173,102,0.15)]'
                      : 'bg-white/[0.04] border-white/10 active:bg-white/[0.08]'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2 min-w-0 pr-2">
                      <Globe className="w-4 h-4 text-[#d4ad66] shrink-0" />
                      <span className="text-[14px] font-medium text-white/90 truncate">
                        {tab.title || 'New Tab'}
                      </span>
                    </div>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onCloseTab(tab.id);
                      }}
                      className="p-1 rounded-full hover:bg-white/20 text-white/50 hover:text-white shrink-0"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                  <div className="font-mono text-[11px] text-white/40 truncate">
                    {tab.url}
                  </div>
                </div>
              );
            })}
          </div>

          <div className="p-4 border-t border-white/10 shrink-0">
            <button
              onClick={() => {
                onCreateTab('blanc://newtab');
                setIsTabsOpen(false);
              }}
              className="w-full h-12 rounded-xl bg-[#d4ad66] text-black font-semibold text-[15px] flex items-center justify-center space-x-2 active:scale-[0.98] transition-transform"
            >
              <Plus className="w-5 h-5 stroke-[2.5]" />
              <span>Open New Tab</span>
            </button>
          </div>
        </div>
      )}

      {/* MOBILE MENU DRAWER */}
      {isMenuOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex flex-col justify-end animate-in fade-in duration-150"
          onClick={() => setIsMenuOpen(false)}
        >
          <div
            className="w-full bg-[#181818] border-t border-white/15 rounded-t-3xl p-5 pb-safe space-y-4 animate-in slide-in-from-bottom duration-200"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="w-12 h-1 bg-white/20 rounded-full mx-auto -mt-1 mb-2" />

            {/* Quick Actions */}
            <div className="grid grid-cols-4 gap-2 text-center text-[12px] text-white/80 py-2 border-b border-white/10">
              <button
                onClick={() => {
                  onAddBookmark({
                    title: activeTab?.title || 'Bookmark',
                    url: activeUrl,
                    folder: 'Mobile',
                    tags: ['mobile'],
                  });
                  setIsMenuOpen(false);
                }}
                className="flex flex-col items-center space-y-1.5 p-2 rounded-xl active:bg-white/10"
              >
                <div className="p-3 bg-white/[0.08] rounded-2xl">
                  <Bookmark className="w-5 h-5 text-[#d4ad66]" />
                </div>
                <span>Bookmark</span>
              </button>

              <button
                onClick={() => {
                  onNavigate('blanc://history');
                  setIsMenuOpen(false);
                }}
                className="flex flex-col items-center space-y-1.5 p-2 rounded-xl active:bg-white/10"
              >
                <div className="p-3 bg-white/[0.08] rounded-2xl">
                  <History className="w-5 h-5 text-sky-400" />
                </div>
                <span>History</span>
              </button>

              <button
                onClick={() => {
                  onNavigate('blanc://bookmarks');
                  setIsMenuOpen(false);
                }}
                className="flex flex-col items-center space-y-1.5 p-2 rounded-xl active:bg-white/10"
              >
                <div className="p-3 bg-white/[0.08] rounded-2xl">
                  <Bookmark className="w-5 h-5 text-amber-400" />
                </div>
                <span>Saved</span>
              </button>

              <button
                onClick={() => {
                  onNavigate('blanc://settings');
                  setIsMenuOpen(false);
                }}
                className="flex flex-col items-center space-y-1.5 p-2 rounded-xl active:bg-white/10"
              >
                <div className="p-3 bg-white/[0.08] rounded-2xl">
                  <Settings className="w-5 h-5 text-purple-400" />
                </div>
                <span>Settings</span>
              </button>
            </div>

            {/* Shield & Security Status */}
            <div className="p-3.5 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <ShieldCheck className="w-6 h-6 text-emerald-400" />
                <div>
                  <div className="text-[14px] font-medium text-white/90">Blanc Shields Active</div>
                  <div className="text-[12px] text-white/50">
                    {adblockStats.totalBlocked} ads & trackers blocked
                  </div>
                </div>
              </div>
            </div>

            <button
              onClick={() => setIsMenuOpen(false)}
              className="w-full h-12 bg-white/10 active:bg-white/15 rounded-xl text-white font-medium text-[15px]"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
};