import React, { useEffect } from 'react';
import { useBrowserIPC } from './hooks/useBrowserIPC';
import { FloatingIsland } from './components/FloatingIsland';
import { TabSwitcher } from './components/TabSwitcher';
import { QuickSwitcher } from './components/QuickSwitcher';
import { NewTabPage } from './components/pages/NewTabPage';
import { HistoryPage } from './components/pages/HistoryPage';
import { SettingsPage } from './components/pages/SettingsPage';
import { BookmarksPage } from './components/pages/BookmarksPage';

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

  // Global Keyboard Shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isMeta = e.metaKey || e.ctrlKey;

      if (isMeta && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        toggleQuickSwitcher();
      } else if (isMeta && e.key.toLowerCase() === 't') {
        e.preventDefault();
        createTab('blanc://newtab');
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
      } else if (isMeta && e.key === ',') {
        e.preventDefault();
        if (activeTab) navigate(activeTab.id, 'blanc://settings');
        else createTab('blanc://settings');
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    activeTab,
    closeTab,
    createTab,
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
  const isInternalPage = isNewTab || isHistory || isSettings || isBookmarks;

  return (
    <div className="relative w-screen h-screen bg-transparent overflow-hidden font-ui select-none">
      {/* TOP FLOATING ISLAND CONTAINER */}
      <header className="fixed top-0 left-0 right-0 h-[72px] flex items-center justify-center pointer-events-none z-30 pt-2">
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
          />
        </div>
      </header>

      {/* MAIN VIEWPORT CONTAINER */}
      <main className="absolute inset-x-3 sm:inset-x-4 top-[74px] bottom-3 sm:bottom-4 rounded-2xl border border-white/10 bg-[#0e0e0e]/95 backdrop-blur-xl shadow-2xl overflow-hidden flex flex-col z-10 pointer-events-auto">
        {/* INTERNAL PAGE: New Tab Page */}
        {isNewTab && (
          <NewTabPage
            favorites={favorites}
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

        {/* EXTERNAL WEB URL DEV PREVIEW (when not in native Tauri or during web development) */}
        {!isInternalPage && (
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
                <span className="px-2 py-0.5 rounded bg-[#d4ad66]/10 text-[#d4ad66] border border-[#d4ad66]/20">
                  {isTauriAvailable ? 'Native WebView Active' : 'Dev Simulation Mode'}
                </span>
              </div>
            </div>

            {/* If not in Tauri, render simulated iframe */}
            {!isTauriAvailable ? (
              <div className="flex-1 w-full h-full relative bg-white">
                <iframe
                  key={activeTab?.id + '-' + activeTab?.url}
                  src={activeTab?.url}
                  title={activeTab?.title || 'Web Page'}
                  className="w-full h-full border-none"
                  sandbox="allow-same-origin allow-scripts allow-forms allow-popups"
                />
              </div>
            ) : (
              <div className="flex-1 w-full h-full" />
            )}
          </div>
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
    </div>
  );
};

export default App;
