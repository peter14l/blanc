import React, { useEffect, useState, useRef } from 'react';
import { useBrowserIPC } from './hooks/useBrowserIPC';
import { FloatingIsland } from './components/FloatingIsland';
import { TabSwitcher } from './components/TabSwitcher';
import { QuickSwitcher } from './components/QuickSwitcher';
import { MobileBrowser } from './components/mobile/MobileBrowser';
import { NewTabPage } from './components/pages/NewTabPage';
import { HistoryPage } from './components/pages/HistoryPage';
import { SettingsPage } from './components/pages/SettingsPage';
import { BookmarksPage } from './components/pages/BookmarksPage';
import { DownloadsPage } from './components/pages/DownloadsPage';
import { ShortcutsPage } from './components/pages/ShortcutsPage';
import { DiagnosticsPage } from './components/pages/DiagnosticsPage';
import { FindCapsule } from './components/FindCapsule';
import { useWebPageLoader } from './hooks/useWebPageLoader';
import { ExternalLink, Loader2 } from 'lucide-react';

export const App: React.FC = () => {
  const {
    tabs,
    activeTab,
    isQuickSwitcherOpen,
    isTabSwitcherOpen,
    isTauriAvailable,
    createTab,
    closeTab,
    closeAllTabs,
    reopenClosedTab,
    switchTab,
    navigate,
    reloadTab,
    goBack,
    goForward,
    minimizeWindow,
    maximizeWindow,
    closeWindow,
    toggleQuickSwitcher,
    toggleTabSwitcher,
    setViewport,
    history,
    removeHistoryEntry,
    clearHistory,
    bookmarks,
    addBookmark,
    removeBookmark,
    updateBookmark,
    favorites,
    addFavorite,
    removeFavorite,
    updateFavorite,
    settings,
    updateSettings,
    adblockStats,
  } = useBrowserIPC();

  const [isFindOpen, setIsFindOpen] = useState(false);
  const [zoomLevel, setZoomLevel] = useState(1.0);

  // Global Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isMeta = e.metaKey || e.ctrlKey;

      if (isMeta && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        toggleQuickSwitcher();
      } else if (isMeta && e.key.toLowerCase() === 'f') {
        e.preventDefault();
        setIsFindOpen((prev) => !prev);
      } else if (isMeta && (e.key === '=' || e.key === '+')) {
        e.preventDefault();
        setZoomLevel((prev) => Math.min(3.0, Number((prev + 0.1).toFixed(1))));
      } else if (isMeta && e.key === '-') {
        e.preventDefault();
        setZoomLevel((prev) => Math.max(0.5, Number((prev - 0.1).toFixed(1))));
      } else if (isMeta && e.key === '0') {
        e.preventDefault();
        setZoomLevel(1.0);
      } else if (isMeta && e.key.toLowerCase() === 't') {
        e.preventDefault();
        if (e.shiftKey) {
          reopenClosedTab();
        } else {
          createTab('blanc://newtab');
        }
      } else if (isMeta && e.key.toLowerCase() === 'w') {
        e.preventDefault();
        if (activeTab) {
          closeTab(activeTab.id);
        }
      } else if (isMeta && e.key.toLowerCase() === 'r') {
        e.preventDefault();
        if (activeTab) {
          reloadTab(activeTab.id);
        }
      } else if (isMeta && (e.key === '[' || e.key === 'ArrowLeft') && e.altKey) {
        e.preventDefault();
        if (activeTab) goBack(activeTab.id);
      } else if (isMeta && (e.key === ']' || e.key === 'ArrowRight') && e.altKey) {
        e.preventDefault();
        if (activeTab) goForward(activeTab.id);
      } else if (isMeta && (e.key.toLowerCase() === 'y' || (e.shiftKey && e.key.toLowerCase() === 'h'))) {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://history');
        else createTab('blanc://history');
      } else if (isMeta && (e.key.toLowerCase() === 'b' && e.shiftKey)) {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://bookmarks');
        else createTab('blanc://bookmarks');
      } else if (isMeta && e.key.toLowerCase() === 'd') {
        e.preventDefault();
        if (activeTab && !bookmarks.some((b) => b.url === activeTab.url)) {
          addBookmark({
            title: activeTab.title || activeTab.url,
            url: activeTab.url,
            folder: 'Bookmarks',
          });
        }
      } else if (isMeta && e.key >= '1' && e.key <= '9') {
        e.preventDefault();
        const targetIdx = e.key === '9' ? tabs.length - 1 : parseInt(e.key, 10) - 1;
        if (tabs[targetIdx]) {
          switchTab(tabs[targetIdx].id);
        }
      } else if (isMeta && e.key === ',') {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://settings');
        else createTab('blanc://settings');
      } else if (isMeta && e.key.toLowerCase() === 'j') {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://downloads');
        else createTab('blanc://downloads');
      } else if (isMeta && e.key === '/') {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://shortcuts');
        else createTab('blanc://shortcuts');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    activeTab,
    tabs,
    bookmarks,
    addBookmark,
    closeTab,
    createTab,
    reopenClosedTab,
    switchTab,
    reloadTab,
    goBack,
    goForward,
    navigate,
    toggleQuickSwitcher,
  ]);

  // Determine current active page type
  const activeUrl = activeTab?.url || 'blanc://newtab';
  const isNewTab =
    activeUrl === 'blanc://newtab' ||
    activeUrl === 'about:blank' ||
    activeUrl === 'about:newtab' ||
    !activeUrl;
  const isHistory = activeUrl === 'blanc://history';
  const isSettings = activeUrl === 'blanc://settings';
  const isBookmarks = activeUrl === 'blanc://bookmarks';
  const isDownloads = activeUrl === 'blanc://downloads';
  const isShortcuts = activeUrl === 'blanc://shortcuts';
  const isDiagnostics = activeUrl === 'blanc://diagnostics';
  const isInternalPage =
    isNewTab ||
    isHistory ||
    isSettings ||
    isBookmarks ||
    isDownloads ||
    isShortcuts ||
    isDiagnostics;
  const checkMobile = () => {
    if (typeof window === 'undefined') return false;
    const ua = navigator.userAgent || '';
    const isTouch = navigator.maxTouchPoints > 0;
    const isMobileUA = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(ua);
    const isSmallScreen = window.innerWidth <= 840;
    return isMobileUA || (isTouch && isSmallScreen) || isSmallScreen;
  };

  const [isMobile, setIsMobile] = useState<boolean>(checkMobile);

  const mainRef = useRef<HTMLElement>(null);

  // Synchronize native embedded child webview viewport with the shell's <main> card
  useEffect(() => {
    if (!isTauriAvailable || isMobile) return;

    const updateNativeViewport = () => {
      if (!mainRef.current) return;
      const rect = mainRef.current.getBoundingClientRect();
      const hidden = isInternalPage || isQuickSwitcherOpen || isTabSwitcherOpen;
      setViewport(rect.left, rect.top, rect.width, rect.height, hidden);
    };

    updateNativeViewport();

    let resizeObserver: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined' && mainRef.current) {
      resizeObserver = new ResizeObserver(() => {
        updateNativeViewport();
      });
      resizeObserver.observe(mainRef.current);
    }

    window.addEventListener('resize', updateNativeViewport);

    return () => {
      if (resizeObserver) resizeObserver.disconnect();
      window.removeEventListener('resize', updateNativeViewport);
    };
  }, [
    isTauriAvailable,
    isMobile,
    activeTab?.id,
    isInternalPage,
    isQuickSwitcherOpen,
    isTabSwitcherOpen,
    setViewport,
  ]);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(checkMobile());
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const { content: desktopPageContent, loading: desktopPageLoading, isDirect: isDesktopDirect, directSrc: desktopDirectSrc } = useWebPageLoader(activeUrl);

  useEffect(() => {
    const handleMessage = (e: MessageEvent) => {
      if (e.data && e.data.type === 'BLANC_NAV' && typeof e.data.url === 'string') {
        if (activeTab) {
          navigate(activeTab.id, e.data.url);
        } else {
          createTab(e.data.url);
        }
      }
    };
    window.addEventListener('message', handleMessage);
    return () => window.removeEventListener('message', handleMessage);
  }, [activeTab, navigate, createTab]);

  if (isMobile) {
    return (
      <MobileBrowser
        tabs={tabs}
        activeTab={activeTab}
        settings={settings}
        adblockStats={adblockStats}
        history={history}
        bookmarks={bookmarks}
        favorites={favorites}
        onNavigate={(url) => {
          if (activeTab) navigate(activeTab.id, url);
          else createTab(url);
        }}
        onCreateTab={createTab}
        onCloseTab={closeTab}
        onCloseAllTabs={closeAllTabs}
        onSwitchTab={switchTab}
        onUpdateSettings={updateSettings}
        onAddBookmark={addBookmark}
        onRemoveBookmark={removeBookmark}
        onUpdateBookmark={updateBookmark}
        onAddFavorite={addFavorite}
        onRemoveFavorite={removeFavorite}
        onUpdateFavorite={updateFavorite}
        onRemoveHistoryEntry={removeHistoryEntry}
        onClearHistory={clearHistory}
      />
    );
  }

  return (
    <div
      className={`relative w-screen h-screen ${
        !isInternalPage && isTauriAvailable ? 'bg-transparent' : 'bg-[#0e0e0e]'
      } text-white overflow-hidden font-ui select-none`}
    >
      {/* TOP FLOATING ISLAND CONTAINER (Blanc Faux Header Strip) */}
      <header className="fixed top-0 left-0 right-0 h-[78px] bg-[#0e0e0e] flex items-center justify-center pointer-events-none z-30 pt-2 transition-colors duration-150">
        <div className="pointer-events-auto w-full flex justify-center px-4">
          <FloatingIsland
            activeTab={activeTab}
            tabsCount={tabs.length}
            searchEngine={settings.searchEngine}
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
            onReload={() => {
              if (activeTab) reloadTab(activeTab.id);
            }}
            onGoBack={() => {
              if (activeTab) goBack(activeTab.id);
            }}
            onGoForward={() => {
              if (activeTab) goForward(activeTab.id);
            }}
            onToggleQuickSwitcher={() => toggleQuickSwitcher()}
            onToggleTabSwitcher={() => toggleTabSwitcher()}
            onMinimize={minimizeWindow}
            onMaximize={maximizeWindow}
            onClose={closeWindow}
            onOpenSettings={() => {
              if (activeTab) navigate(activeTab.id, 'blanc://settings');
              else createTab('blanc://settings');
            }}
            onOpenBookmarks={() => {
              if (activeTab) navigate(activeTab.id, 'blanc://bookmarks');
              else createTab('blanc://bookmarks');
            }}
            onOpenHistory={() => {
              if (activeTab) navigate(activeTab.id, 'blanc://history');
              else createTab('blanc://history');
            }}
            history={history}
            bookmarks={bookmarks}
          />
        </div>
      </header>

      {/* MAIN VIEWPORT CONTAINER */}
      <main
        ref={mainRef}
        style={{ zoom: zoomLevel }}
        className={`absolute inset-x-3 sm:inset-x-4 top-[80px] bottom-3 sm:bottom-4 rounded-2xl border border-white/10 shadow-2xl overflow-hidden flex flex-col z-10 ${
          !isInternalPage && isTauriAvailable
            ? 'bg-transparent pointer-events-none'
            : 'bg-[#0e0e0e] pointer-events-auto'
        }`}
      >
        {/* INTERNAL PAGE: New Tab Page */}
        {isNewTab && (
          <NewTabPage
            favorites={favorites}
            history={history}
            bookmarks={bookmarks}
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
            onAddFavorite={addFavorite}
            onRemoveFavorite={removeFavorite}
            onUpdateFavorite={updateFavorite}
            settings={settings}
            adblockStats={adblockStats}
            currentTabBlockedTrackers={activeTab?.blocked_trackers || 0}
          />
        )}

        {/* INTERNAL PAGE: History */}
        {isHistory && (
          <HistoryPage
            history={history}
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
            onOpenNewTab={(url) => createTab(url)}
            onRemoveEntry={removeHistoryEntry}
            onClearHistory={clearHistory}
          />
        )}

        {/* INTERNAL PAGE: Settings */}
        {isSettings && (
          <SettingsPage
            settings={settings}
            onUpdateSettings={updateSettings}
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
          />
        )}

        {/* INTERNAL PAGE: Bookmarks */}
        {isBookmarks && (
          <BookmarksPage
            bookmarks={bookmarks}
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
            onOpenNewTab={(url) => createTab(url)}
            onAddBookmark={addBookmark}
            onRemoveBookmark={removeBookmark}
            onUpdateBookmark={updateBookmark}
          />
        )}

        {/* INTERNAL PAGE: Downloads */}
        {isDownloads && (
          <DownloadsPage
            onNavigate={(url) => {
              if (activeTab) navigate(activeTab.id, url);
              else createTab(url);
            }}
          />
        )}

        {/* INTERNAL PAGE: Shortcuts */}
        {isShortcuts && <ShortcutsPage />}

        {/* INTERNAL PAGE: Diagnostics */}
        {isDiagnostics && (
          <DiagnosticsPage
            tabs={tabs}
            settings={settings}
            adblockStats={adblockStats}
          />
        )}

        {/* EXTERNAL WEB URL */}
        {!isInternalPage && (
          isTauriAvailable ? (
            /* In native Tauri, the child webview is natively embedded in this viewport. */
            <div className="flex-1 w-full h-full relative bg-transparent flex flex-col overflow-hidden pointer-events-none">
              {activeTab?.is_loading && (
                <div className="absolute top-0 left-0 right-0 h-1 bg-[#d4ad66]/30 z-20 overflow-hidden pointer-events-auto">
                  <div className="w-full h-full bg-[#d4ad66] animate-pulse origin-left" />
                </div>
              )}
            </div>
          ) : (
            /* In dev web preview (browser mode without Tauri), render iframe preview */
            <div className="flex-1 w-full h-full flex flex-col">
              {/* Header bar indicating external web page */}
              <div className="flex items-center justify-between px-4 py-2 border-b border-white/10 bg-white/[0.02] text-[12px] text-white/50 shrink-0">
                <div className="flex items-center space-x-2 min-w-0">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse shrink-0"></span>
                  <span className="font-mono text-white/80 truncate max-w-xs">
                    {activeTab?.title || 'Web View'}
                  </span>
                  <span className="text-white/30 hidden sm:inline">•</span>
                  <span className="font-mono text-[11px] text-white/40 truncate max-w-sm hidden sm:inline">
                    {activeTab?.url}
                  </span>
                </div>
                <div className="flex items-center space-x-2 text-[11px] shrink-0">
                  <button
                    onClick={() => window.open(activeUrl, '_blank')}
                    title="Open in System Browser"
                    className="px-2 py-0.5 rounded bg-white/5 hover:bg-white/10 text-white/70 hover:text-white flex items-center space-x-1 border border-white/10 transition-colors"
                  >
                    <ExternalLink className="w-3 h-3" />
                    <span>Open External</span>
                  </button>
                  <span className="px-2 py-0.5 rounded bg-[#d4ad66]/10 text-[#d4ad66] border border-[#d4ad66]/20">
                    Live Web View
                  </span>
                </div>
              </div>

              {/* Web Viewport with srcDoc and direct iframe support */}
              <div className="flex-1 w-full h-full relative bg-[#0e0e0e] flex flex-col overflow-hidden">
                {desktopPageLoading && (
                  <div className="absolute top-0 left-0 right-0 h-1 bg-[#d4ad66]/30 z-20 overflow-hidden">
                    <div className="w-full h-full bg-[#d4ad66] animate-pulse origin-left" />
                  </div>
                )}

                {desktopPageLoading && !desktopPageContent && !isDesktopDirect ? (
                  <div className="flex-1 w-full h-full flex flex-col items-center justify-center bg-[#0d0d0d] text-white p-6">
                    <Loader2 className="w-8 h-8 text-[#d4ad66] animate-spin mb-3" />
                    <p className="text-[15px] font-medium text-white/90">Loading {activeTab?.title || activeUrl}...</p>
                    <p className="text-[12px] text-white/40 mt-1 max-w-sm text-center truncate">{activeUrl}</p>
                  </div>
                ) : desktopPageContent ? (
                  <iframe
                    key={'content-' + activeTab?.id + '-' + activeTab?.url}
                    srcDoc={desktopPageContent}
                    title={activeTab?.title || 'Web Page'}
                    className="w-full h-full border-none flex-1"
                    sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals allow-downloads"
                  />
                ) : (
                  <iframe
                    key={'direct-' + activeTab?.id + '-' + desktopDirectSrc}
                    src={desktopDirectSrc}
                    title={activeTab?.title || 'Web Page'}
                    className="w-full h-full border-none flex-1"
                    sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals allow-downloads"
                  />
                )}
              </div>
            </div>
          )
        )}
      </main>

      {/* TAB SWITCHER MODAL */}
      <TabSwitcher
        isOpen={isTabSwitcherOpen}
        tabs={tabs}
        activeTabId={activeTab?.id}
        onSelectTab={(tabId) => {
          switchTab(tabId);
          toggleTabSwitcher(false);
        }}
        onCloseTab={(tabId) => {
          closeTab(tabId);
        }}
        onCloseAllTabs={closeAllTabs}
        onNewTab={() => {
          createTab('blanc://newtab');
          toggleTabSwitcher(false);
        }}
        onClose={() => toggleTabSwitcher(false)}
      />

      {/* QUICK SWITCHER COMMAND PALETTE */}
      <QuickSwitcher
        isOpen={isQuickSwitcherOpen}
        tabs={tabs}
        bookmarks={bookmarks}
        history={history}
        settings={settings}
        onSelectTab={(tabId) => {
          switchTab(tabId);
        }}
        onNavigate={(url) => {
          if (activeTab) {
            navigate(activeTab.id, url);
          } else {
            createTab(url);
          }
        }}
        onNewTab={(url) => {
          createTab(url || 'blanc://newtab');
        }}
        onClose={() => toggleQuickSwitcher(false)}
        onClearHistory={clearHistory}
        onSetTheme={(theme) => updateSettings({ theme })}
      />

      {/* IN-PAGE FIND CAPSULE */}
      <FindCapsule
        isOpen={isFindOpen}
        onClose={() => setIsFindOpen(false)}
      />
    </div>
  );
};

export default App;
