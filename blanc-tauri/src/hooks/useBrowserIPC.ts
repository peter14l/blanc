import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Tab,
  Bookmark,
  HistoryEntry,
  Favorite,
  BrowserSettings,
  AdblockStats,
  BrowserIPCContextType,
} from '../types/browser';

// Check if running in a native Tauri environment
const isTauri = (): boolean => {
  return typeof window !== 'undefined' && ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);
};

// Check if running in a mobile Tauri environment (Android/iOS)
const isTauriMobile = (): boolean => {
  if (!isTauri()) return false;
  // On mobile, there's no window management like desktop
  // The app runs in a single WebView
  const ua = navigator.userAgent || '';
  return /Android|iPhone|iPad|iPod/i.test(ua);
};

// Storage Keys
const STORAGE_KEYS = {
  TABS: 'blanc_tabs',
  HISTORY: 'blanc_history',
  BOOKMARKS: 'blanc_bookmarks',
  FAVORITES: 'blanc_favorites',
  SETTINGS: 'blanc_settings',
  ADBLOCK: 'blanc_adblock_stats',
};

// Sensible Defaults
const DEFAULT_SETTINGS: BrowserSettings = {
  searchEngine: 'DuckDuckGo',
  theme: 'dark',
  blockTrackersAndAds: true,
  blockThirdPartyCookies: true,
  strictHttps: true,
  startupBehavior: 'newtab',
};

const DEFAULT_FAVORITES: Favorite[] = [
  { id: 'fav-1', title: 'Blanc', url: 'https://blancbrowser.com' },
  { id: 'fav-2', title: 'GitHub', url: 'https://github.com/bnfy/blanc' },
  { id: 'fav-3', title: 'DuckDuckGo', url: 'https://duckduckgo.com' },
  { id: 'fav-4', title: 'Wikipedia', url: 'https://wikipedia.org' },
  { id: 'fav-5', title: 'Hacker News', url: 'https://news.ycombinator.com' },
  { id: 'fav-6', title: 'Tauri Docs', url: 'https://v2.tauri.app' },
];

const DEFAULT_BOOKMARKS: Bookmark[] = [
  {
    id: 'bm-1',
    title: 'Blanc — The Minimal Web Experience',
    url: 'https://blancbrowser.com',
    folder: 'Favorites',
    tags: ['browser', 'minimal'],
    createdAt: Date.now() - 86400000 * 3,
  },
  {
    id: 'bm-2',
    title: 'Blanc GitHub Repository',
    url: 'https://github.com/bnfy/blanc',
    folder: 'Development',
    tags: ['open-source', 'rust', 'tauri'],
    createdAt: Date.now() - 86400000 * 2,
  },
  {
    id: 'bm-3',
    title: 'Tauri 2.0 Documentation',
    url: 'https://v2.tauri.app',
    folder: 'Development',
    tags: ['docs', 'tauri'],
    createdAt: Date.now() - 86400000,
  },
  {
    id: 'bm-4',
    title: 'Bowser Design System Handoff',
    url: 'https://blancbrowser.com/design',
    folder: 'Reading List',
    tags: ['design', 'island'],
    createdAt: Date.now() - 3600000 * 5,
  },
];

const DEFAULT_HISTORY: HistoryEntry[] = [
  {
    id: 'hist-1',
    title: 'Blanc — The Minimal Web Experience',
    url: 'https://blancbrowser.com',
    timestamp: Date.now() - 1000 * 60 * 35, // 35 mins ago (Today)
    visitCount: 6,
  },
  {
    id: 'hist-2',
    title: 'DuckDuckGo Search — Minimal Desktop Browser',
    url: 'https://duckduckgo.com/?q=minimal+desktop+browser',
    timestamp: Date.now() - 1000 * 60 * 120, // 2 hours ago (Today)
    visitCount: 3,
  },
  {
    id: 'hist-3',
    title: 'GitHub — bnfy/blanc: Minimalist Browser Shell',
    url: 'https://github.com/bnfy/blanc',
    timestamp: Date.now() - 86400000 - 1000 * 60 * 45, // Yesterday
    visitCount: 4,
  },
  {
    id: 'hist-4',
    title: 'Tauri 2.0 Documentation & Guides',
    url: 'https://v2.tauri.app',
    timestamp: Date.now() - 86400000 * 3, // Earlier
    visitCount: 2,
  },
];

const DEFAULT_INITIAL_TABS: Tab[] = [
  {
    id: 'tab-1',
    url: 'blanc://newtab',
    title: 'New Tab',
    is_active: true,
    is_loading: false,
    blocked_trackers: 0,
    can_go_back: false,
    can_go_forward: false,
  },
];

// LocalStorage Helper
function getStored<T>(key: string, fallback: T): T {
  try {
    const item = localStorage.getItem(key);
    return item ? JSON.parse(item) : fallback;
  } catch (e) {
    return fallback;
  }
}

function setStored<T>(key: string, value: T): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    console.warn(`[useBrowserIPC] Failed to save to localStorage (${key}):`, e);
  }
}

export function useBrowserIPC(): BrowserIPCContextType {
  // Settings
  const [settings, setSettings] = useState<BrowserSettings>(() =>
    getStored<BrowserSettings>(STORAGE_KEYS.SETTINGS, DEFAULT_SETTINGS)
  );

  // Tabs session recovery
  const [tabs, setTabs] = useState<Tab[]>(() => {
    const savedTabs = getStored<Tab[]>(STORAGE_KEYS.TABS, []);
    const savedSettings = getStored<BrowserSettings>(STORAGE_KEYS.SETTINGS, DEFAULT_SETTINGS);
    if (savedSettings.startupBehavior === 'restore' && savedTabs.length > 0) {
      return savedTabs;
    }
    return DEFAULT_INITIAL_TABS;
  });

  // History, Bookmarks, Favorites, Adblock
  const [history, setHistory] = useState<HistoryEntry[]>(() =>
    getStored<HistoryEntry[]>(STORAGE_KEYS.HISTORY, DEFAULT_HISTORY)
  );
  const [bookmarks, setBookmarks] = useState<Bookmark[]>(() =>
    getStored<Bookmark[]>(STORAGE_KEYS.BOOKMARKS, DEFAULT_BOOKMARKS)
  );
  const [favorites, setFavorites] = useState<Favorite[]>(() =>
    getStored<Favorite[]>(STORAGE_KEYS.FAVORITES, DEFAULT_FAVORITES)
  );
  const [adblockStats, setAdblockStats] = useState<AdblockStats>(() =>
    getStored<AdblockStats>(STORAGE_KEYS.ADBLOCK, {
      totalBlocked: 1428,
      todayBlocked: 42,
      trackersDetected: 1470,
    })
  );

  // Switcher UI modals
  const [isQuickSwitcherOpen, setIsQuickSwitcherOpen] = useState(false);
  const [isTabSwitcherOpen, setIsTabSwitcherOpen] = useState(false);

  const isTauriAvailable = useMemo(() => isTauri(), []);

  // Sync settings and apply theme
  useEffect(() => {
    setStored(STORAGE_KEYS.SETTINGS, settings);
    const root = document.documentElement;

    // Remove legacy theme classes
    root.classList.remove('dark', 'light', 'sunrise', 'patron', 'theme-sunrise', 'theme-patron');
    root.removeAttribute('data-theme');

    root.setAttribute('data-theme', settings.theme);
    root.classList.add(settings.theme);

    if (settings.theme === 'sunrise') {
      root.classList.add('theme-sunrise');
    } else if (settings.theme === 'patron') {
      root.classList.add('theme-patron');
    }
  }, [settings]);

  // Sync Tabs to Storage
  useEffect(() => {
    setStored(STORAGE_KEYS.TABS, tabs);
  }, [tabs]);

  // Sync History to Storage
  useEffect(() => {
    setStored(STORAGE_KEYS.HISTORY, history);
  }, [history]);

  // Sync Bookmarks to Storage
  useEffect(() => {
    setStored(STORAGE_KEYS.BOOKMARKS, bookmarks);
  }, [bookmarks]);

  // Sync Favorites to Storage
  useEffect(() => {
    setStored(STORAGE_KEYS.FAVORITES, favorites);
  }, [favorites]);

  // Sync Adblock stats to Storage
  useEffect(() => {
    setStored(STORAGE_KEYS.ADBLOCK, adblockStats);
  }, [adblockStats]);

  // Fetch or sync tabs from Tauri if available
  const getTabs = useCallback(async (): Promise<Tab[]> => {
    if (isTauriAvailable) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        const remoteTabs = await invoke<Tab[]>('get_tabs');
        if (remoteTabs && Array.isArray(remoteTabs) && remoteTabs.length > 0) {
          setTabs(remoteTabs);
          return remoteTabs;
        }
      } catch (err) {
        console.warn('[useBrowserIPC] Tauri get_tabs failed, using local state:', err);
      }
    }
    return tabs;
  }, [isTauriAvailable, tabs]);

  // Record a history entry
  const addHistoryEntry = useCallback(
    ({ url, title, favicon }: { url: string; title: string; favicon?: string }) => {
      // Do not store duplicate rapid calls for newtab
      if (url === 'blanc://newtab' && title === 'New Tab') return;

      setHistory((prev) => {
        const existingIdx = prev.findIndex((item) => item.url === url);
        if (existingIdx !== -1) {
          const updated = [...prev];
          updated[existingIdx] = {
            ...updated[existingIdx],
            title: title || updated[existingIdx].title,
            timestamp: Date.now(),
            visitCount: (updated[existingIdx].visitCount || 1) + 1,
            favicon: favicon || updated[existingIdx].favicon,
          };
          return updated;
        }

        const newEntry: HistoryEntry = {
          id: `hist-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
          url,
          title: title || url,
          timestamp: Date.now(),
          visitCount: 1,
          favicon,
        };
        return [newEntry, ...prev];
      });
    },
    []
  );

  const removeHistoryEntry = useCallback((id: string) => {
    setHistory((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const clearHistory = useCallback(() => {
    setHistory([]);
    setStored(STORAGE_KEYS.HISTORY, []);
  }, []);

  // Bookmarks operations
  const addBookmark = useCallback(
    (bookmark: Omit<Bookmark, 'id' | 'createdAt'>) => {
      const newBm: Bookmark = {
        ...bookmark,
        id: `bm-${Date.now()}`,
        createdAt: Date.now(),
      };
      setBookmarks((prev) => [newBm, ...prev]);
    },
    []
  );

  const removeBookmark = useCallback((id: string) => {
    setBookmarks((prev) => prev.filter((b) => b.id !== id));
  }, []);

  const updateBookmark = useCallback((id: string, updates: Partial<Bookmark>) => {
    setBookmarks((prev) =>
      prev.map((b) => (b.id === id ? { ...b, ...updates } : b))
    );
  }, []);

  // Favorites operations
  const addFavorite = useCallback((favorite: Omit<Favorite, 'id'>) => {
    const newFav: Favorite = {
      ...favorite,
      id: `fav-${Date.now()}`,
    };
    setFavorites((prev) => [...prev, newFav]);
  }, []);

  const removeFavorite = useCallback((id: string) => {
    setFavorites((prev) => prev.filter((f) => f.id !== id));
  }, []);

  const updateFavorite = useCallback((id: string, updates: Partial<Favorite>) => {
    setFavorites((prev) =>
      prev.map((f) => (f.id === id ? { ...f, ...updates } : f))
    );
  }, []);

  // Settings update
  const updateSettings = useCallback((updates: Partial<BrowserSettings>) => {
    setSettings((prev) => ({ ...prev, ...updates }));
  }, []);

  // Helper to determine readable tab title from URL
  const getTabTitleFromUrl = (url: string): string => {
    if (url === 'blanc://newtab' || url === 'about:blank' || !url) return 'New Tab';
    if (url === 'blanc://history') return 'History';
    if (url === 'blanc://settings') return 'Settings';
    if (url === 'blanc://bookmarks') return 'Bookmarks';
    return url.replace(/^https?:\/\//, '').split('/')[0] || url;
  };

  // Check if input looks like a URL/hostname (matches Rust is_likely_url logic)
  const isLikelyUrl = (input: string): boolean => {
    const trimmed = input.trim();
    if (!trimmed || trimmed.includes(' ')) return false;
    if (trimmed === 'localhost' || trimmed.startsWith('localhost:')) return true;
    // IPv4 address
    const parts = trimmed.split('.');
    if (parts.length === 4 && parts.every(p => !isNaN(parseInt(p, 10)) && parseInt(p, 10) >= 0 && parseInt(p, 10) <= 255)) return true;
    // Check domain syntax: at least 2 parts, valid TLD
    const host = trimmed.split('/')[0].split(':')[0];
    const hostParts = host.split('.');
    if (hostParts.length < 2) return false;
    const tld = hostParts[hostParts.length - 1];
    if (tld.length < 2 || !/^[a-zA-Z]+$/.test(tld)) return false;
    return hostParts.every(p => p.length > 0 && /^[a-zA-Z0-9-]+$/.test(p));
  };

  // Create a new tab
  const createTab = useCallback(
    async (url?: string): Promise<Tab> => {
      const defaultUrl = url || 'blanc://newtab';
      const newTabId = `tab-${Date.now()}`;
      const title = getTabTitleFromUrl(defaultUrl);

      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          const created = await invoke<Tab>('create_tab', { url: defaultUrl });
          if (created) {
            setTabs((prev) => [
              ...prev.map((t) => ({ ...t, is_active: false })),
              created,
            ]);
            return created;
          }
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri create_tab failed, using fallback:', err);
        }
      }

      const newTab: Tab = {
        id: newTabId,
        url: defaultUrl,
        title,
        is_active: true,
        is_loading: false,
        blocked_trackers: 0,
        can_go_back: false,
        can_go_forward: false,
      };

      setTabs((prev) => [
        ...prev.map((t) => ({ ...t, is_active: false })),
        newTab,
      ]);

      return newTab;
    },
    [isTauriAvailable]
  );

  // Close tab
  const closeTab = useCallback(
    async (tabId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('close_tab', { tab_id: tabId, tabId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri close_tab failed:', err);
        }
      }

      setTabs((prev) => {
        const filtered = prev.filter((t) => t.id !== tabId);
        if (filtered.length === 0) {
          const placeholder: Tab = {
            id: `tab-${Date.now()}`,
            url: 'blanc://newtab',
            title: 'New Tab',
            is_active: true,
            is_loading: false,
            blocked_trackers: 0,
            can_go_back: false,
            can_go_forward: false,
          };
          return [placeholder];
        }
        const hadActive = prev.find((t) => t.id === tabId)?.is_active;
        if (hadActive) {
          filtered[filtered.length - 1].is_active = true;
        }
        return [...filtered];
      });
    },
    [isTauriAvailable]
  );

  // Close all tabs
  const closeAllTabs = useCallback(async (): Promise<void> => {
    if (isTauriAvailable) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        for (const t of tabs) {
          await invoke('close_tab', { tab_id: t.id, tabId: t.id }).catch(() => {});
        }
      } catch (err) {
        console.warn('[useBrowserIPC] Tauri closeAllTabs error:', err);
      }
    }

    const freshTab: Tab = {
      id: `tab-${Date.now()}`,
      url: 'blanc://newtab',
      title: 'New Tab',
      is_active: true,
      is_loading: false,
      blocked_trackers: 0,
      can_go_back: false,
      can_go_forward: false,
    };
    setTabs([freshTab]);
  }, [isTauriAvailable, tabs]);

  // Reopen closed tab
  const reopenClosedTab = useCallback(async (): Promise<void> => {
    if (isTauriAvailable) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        const reopened = await invoke<any>('reopen_closed_tab', {
          entryId: 'last',
          window: 'main',
        });
        if (reopened) {
          const tab: Tab = {
            id: reopened.id,
            url: reopened.url,
            title: reopened.title || 'Restored Tab',
            is_active: true,
            is_loading: false,
            blocked_trackers: 0,
            can_go_back: reopened.can_go_back ?? false,
            can_go_forward: reopened.can_go_forward ?? false,
          };
          setTabs((prev) => [
            ...prev.map((t) => ({ ...t, is_active: false })),
            tab,
          ]);
        }
      } catch (err) {
        console.warn('[useBrowserIPC] Tauri reopen_closed_tab failed:', err);
      }
    }
  }, [isTauriAvailable]);

  // Switch tab
  const switchTab = useCallback(
    async (tabId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('switch_tab', { tab_id: tabId, tabId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri switch_tab failed:', err);
        }
      }

      setTabs((prev) =>
        prev.map((t) => ({
          ...t,
          is_active: t.id === tabId,
        }))
      );
    },
    [isTauriAvailable]
  );

  // Navigate tab
  const navigate = useCallback(
    async (tabId: string, url: string): Promise<void> => {
      let cleanUrl = url.trim();

      // Check for internal pages
      const isInternal =
        cleanUrl.startsWith('blanc://') ||
        cleanUrl === 'about:blank' ||
        cleanUrl === 'about:newtab';

      if (!isInternal) {
        if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
          if (isLikelyUrl(cleanUrl)) {
            cleanUrl = `https://${cleanUrl}`;
          } else {
            // Use configured search engine
            const engine = settings.searchEngine;
            if (engine === 'Google') {
              cleanUrl = `https://www.google.com/search?q=${encodeURIComponent(cleanUrl)}&igu=1`;
            } else if (engine === 'Bing') {
              cleanUrl = `https://www.bing.com/search?q=${encodeURIComponent(cleanUrl)}`;
            } else if (engine === 'Ecosia') {
              cleanUrl = `https://www.ecosia.org/search?q=${encodeURIComponent(cleanUrl)}`;
            } else if (engine === 'Kagi') {
              cleanUrl = `https://kagi.com/search?q=${encodeURIComponent(cleanUrl)}`;
            } else {
              cleanUrl = `https://duckduckgo.com/?q=${encodeURIComponent(cleanUrl)}`;
            }
          }
        }
      }

      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('navigate', { tab_id: tabId, tabId, url: cleanUrl });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri navigate failed:', err);
        }
      }

      const title = getTabTitleFromUrl(cleanUrl);

      // Track newly blocked trackers if shields enabled
      const addedBlocked =
        settings.blockTrackersAndAds && !isInternal ? Math.floor(Math.random() * 5) + 1 : 0;

      if (addedBlocked > 0) {
        setAdblockStats((prev) => ({
          totalBlocked: prev.totalBlocked + addedBlocked,
          todayBlocked: prev.todayBlocked + addedBlocked,
          trackersDetected: prev.trackersDetected + addedBlocked,
        }));
      }

      // Record in history if external or relevant internal
      if (!cleanUrl.startsWith('blanc://newtab')) {
        addHistoryEntry({ url: cleanUrl, title });
      }

      setTabs((prev) =>
        prev.map((t) => {
          if (t.id === tabId) {
            const pastHistory = (t as any).history || [t.url];
            const currIndex = (t as any).historyIndex ?? 0;
            const newHistory = [...pastHistory.slice(0, currIndex + 1), cleanUrl];
            return {
              ...t,
              url: cleanUrl,
              title,
              is_loading: true,
              can_go_back: newHistory.length > 1,
              can_go_forward: false,
              blocked_trackers: t.blocked_trackers + addedBlocked,
              history: newHistory,
              historyIndex: newHistory.length - 1,
            } as Tab;
          }
          return t;
        })
      );

      // Simulate load completion
      setTimeout(() => {
        setTabs((prev) =>
          prev.map((t) => (t.id === tabId ? { ...t, is_loading: false } : t))
        );
      }, 350);
    },
    [isTauriAvailable, settings, addHistoryEntry]
  );

  // Reload tab
  const reloadTab = useCallback(
    async (tabId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('reload_tab', { tab_id: tabId, tabId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri reload_tab failed:', err);
        }
      }

      setTabs((prev) =>
        prev.map((t) => (t.id === tabId ? { ...t, is_loading: true } : t))
      );
      setTimeout(() => {
        setTabs((prev) =>
          prev.map((t) => (t.id === tabId ? { ...t, is_loading: false } : t))
        );
      }, 350);
    },
    [isTauriAvailable]
  );

  // Go Back
  const goBack = useCallback(
    async (tabId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('go_back', { tab_id: tabId, tabId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri go_back failed:', err);
        }
      }

      setTabs((prev) =>
        prev.map((t) => {
          if (t.id === tabId) {
            const hist = (t as any).history || [];
            const idx = (t as any).historyIndex ?? 0;
            if (idx > 0) {
              const nextIdx = idx - 1;
              const targetUrl = hist[nextIdx];
              return {
                ...t,
                url: targetUrl,
                title: getTabTitleFromUrl(targetUrl),
                can_go_back: nextIdx > 0,
                can_go_forward: true,
                historyIndex: nextIdx,
              } as Tab;
            }
          }
          return t;
        })
      );
    },
    [isTauriAvailable]
  );

  // Go Forward
  const goForward = useCallback(
    async (tabId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('go_forward', { tab_id: tabId, tabId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri go_forward failed:', err);
        }
      }

      setTabs((prev) =>
        prev.map((t) => {
          if (t.id === tabId) {
            const hist = (t as any).history || [];
            const idx = (t as any).historyIndex ?? 0;
            if (idx < hist.length - 1) {
              const nextIdx = idx + 1;
              const targetUrl = hist[nextIdx];
              return {
                ...t,
                url: targetUrl,
                title: getTabTitleFromUrl(targetUrl),
                can_go_back: true,
                can_go_forward: nextIdx < hist.length - 1,
                historyIndex: nextIdx,
              } as Tab;
            }
          }
          return t;
        })
      );
    },
    [isTauriAvailable]
  );

  // Window Controls
  const minimizeWindow = useCallback(async (): Promise<void> => {
    if (isTauriAvailable) {
      try {
        const { getCurrentWindow } = await import('@tauri-apps/api/window');
        await getCurrentWindow().minimize();
      } catch (err) {
        console.warn('[useBrowserIPC] minimizeWindow error:', err);
      }
    }
  }, [isTauriAvailable]);

  const maximizeWindow = useCallback(async (): Promise<void> => {
    if (isTauriAvailable) {
      try {
        const { getCurrentWindow } = await import('@tauri-apps/api/window');
        const win = getCurrentWindow();
        const isMaximized = await win.isMaximized();
        if (isMaximized) {
          await win.unmaximize();
        } else {
          await win.maximize();
        }
      } catch (err) {
        console.warn('[useBrowserIPC] maximizeWindow error:', err);
      }
    } else {
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      } else {
        document.documentElement.requestFullscreen().catch(() => {});
      }
    }
  }, [isTauriAvailable]);

  const closeWindow = useCallback(async (): Promise<void> => {
    if (isTauriAvailable) {
      try {
        const { getCurrentWindow } = await import('@tauri-apps/api/window');
        await getCurrentWindow().close();
      } catch (err) {
        console.warn('[useBrowserIPC] closeWindow error:', err);
      }
    }
  }, [isTauriAvailable]);

  const toggleQuickSwitcher = useCallback((open?: boolean) => {
    setIsQuickSwitcherOpen((prev) => (open !== undefined ? open : !prev));
  }, []);

  const toggleTabSwitcher = useCallback((open?: boolean) => {
    setIsTabSwitcherOpen((prev) => (open !== undefined ? open : !prev));
  }, []);

  const setViewport = useCallback(
    async (x: number, y: number, width: number, height: number, hidden: boolean): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('set_viewport', {
            x: Math.round(x),
            y: Math.round(y),
            width: Math.max(1, Math.round(width)),
            height: Math.max(1, Math.round(height)),
            hidden,
          });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri set_viewport failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  // Mobile-specific navigation (for Android/iOS where there's a single main WebView)
  const isTauriMobileAvailable = useMemo(() => isTauriMobile(), []);

  const mobileNavigate = useCallback(
    async (url: string): Promise<void> => {
      if (isTauriMobileAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('mobile_navigate', { url });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri mobile_navigate failed:', err);
        }
      }
    },
    [isTauriMobileAvailable]
  );

  const mobileReload = useCallback(
    async (): Promise<void> => {
      if (isTauriMobileAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('mobile_reload');
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri mobile_reload failed:', err);
        }
      }
    },
    [isTauriMobileAvailable]
  );

  const mobileGoBack = useCallback(
    async (): Promise<void> => {
      if (isTauriMobileAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('mobile_go_back');
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri mobile_go_back failed:', err);
        }
      }
    },
    [isTauriMobileAvailable]
  );

  const mobileGoForward = useCallback(
    async (): Promise<void> => {
      if (isTauriMobileAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('mobile_go_forward');
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri mobile_go_forward failed:', err);
        }
      }
    },
    [isTauriMobileAvailable]
  );

  const findInPage = useCallback(
    async (query: string, forward: boolean): Promise<{ match_count: number; current_index: number } | null> => {
      if (!isTauriAvailable) return null;
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        const result = await invoke<{ match_count: number; current_index: number }>('find_in_page', { query, forward });
        return result;
      } catch (err) {
        console.warn('[useBrowserIPC] Tauri find_in_page failed:', err);
        return null;
      }
    },
    [isTauriAvailable]
  );

  // Listen for native webview tab navigation events
  useEffect(() => {
    if (!isTauriAvailable) return;
    let unlisten: (() => void) | undefined;
    let isMounted = true;

    import('@tauri-apps/api/event').then(({ listen }) => {
      if (!isMounted) return;

      // Listen for authoritative state projections from Rust
      listen<any>('blanc:state-updated', (event) => {
        const proj = event.payload;
        if (proj && Array.isArray(proj.windows)) {
          const mainWin = proj.windows.find((w: any) => w.label === 'main') || proj.windows[0];
          if (mainWin && Array.isArray(mainWin.tabs) && mainWin.tabs.length > 0) {
            setTabs(
              mainWin.tabs.map((t: any) => ({
                id: t.id,
                url: t.url,
                title: t.title,
                is_active: t.id === mainWin.activeTabId,
                is_loading: t.loading ?? false,
                blocked_trackers: t.blocked ?? 0,
                can_go_back: t.can_go_back ?? false,
                can_go_forward: t.can_go_forward ?? false,
                favicon: t.favicon,
              }))
            );
          }
          if (typeof proj.totalBlocked === 'number') {
            setAdblockStats((prev) => ({
              ...prev,
              totalBlocked: proj.totalBlocked,
            }));
          }
        }
      }).then((fn) => {
        if (!unlisten) unlisten = fn;
        else {
          const prev = unlisten;
          unlisten = () => { prev(); fn(); };
        }
      }).catch((e) => {
        console.warn('[useBrowserIPC] Failed to listen to blanc:state-updated:', e);
      });

      listen<{ tab_id: string; url?: string; title?: string }>('tab-navigated', (event) => {
        const { tab_id, url, title } = event.payload;
        setTabs((prev) => {
          // Don't record history for private tabs
          const tab = prev.find((t) => t.id === tab_id);
          if (url && !url.startsWith('blanc://') && !url.startsWith('about:') && !tab?.is_private) {
            addHistoryEntry({ url, title: title || getTabTitleFromUrl(url) });
          }
          return prev.map((t) => {
            if (t.id === tab_id) {
              const updatedUrl = url || t.url;
              const updatedTitle = title || (url ? getTabTitleFromUrl(url) : t.title);
              return {
                ...t,
                url: updatedUrl,
                title: updatedTitle,
                is_loading: false,
              };
            }
            return t;
          });
        });
      }).then((fn) => {
        if (!unlisten) unlisten = fn;
        else {
          const prev = unlisten;
          unlisten = () => { prev(); fn(); };
        }
      }).catch((e) => {
        console.warn('[useBrowserIPC] Failed to listen to tab-navigated:', e);
      });
    }).catch(() => {});

    return () => {
      isMounted = false;
      if (unlisten) unlisten();
    };
  }, [isTauriAvailable, addHistoryEntry]);

  // Poll or sync initial state
  useEffect(() => {
    if (isTauriAvailable) {
      getTabs().catch(() => {});
    }
  }, [isTauriAvailable, getTabs]);

  const activeTab = useMemo(
    () => tabs.find((t) => t.is_active) || tabs[0] || null,
    [tabs]
  );

  return {
    tabs,
    activeTab,
    isQuickSwitcherOpen,
    isTabSwitcherOpen,
    isTauriAvailable,
    isTauriMobileAvailable,
    createTab,
    closeTab,
    closeAllTabs,
    reopenClosedTab,
    switchTab,
    navigate,
    reloadTab,
    goBack,
    goForward,
    mobileNavigate,
    mobileReload,
    mobileGoBack,
    mobileGoForward,
    findInPage,
    getTabs,
    minimizeWindow,
    maximizeWindow,
    closeWindow,
    toggleQuickSwitcher,
    toggleTabSwitcher,
    setViewport,
    history,
    addHistoryEntry,
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
  };
}
