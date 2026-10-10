export interface Tab {
  id: string;
  url: string;
  title: string;
  is_active: boolean;
  is_loading: boolean;
  blocked_trackers: number;
  can_go_back: boolean;
  can_go_forward: boolean;
  favicon?: string;
  is_private?: boolean;
  is_asleep?: boolean;
  history?: string[];
  historyIndex?: number;
}

export type QuickSwitcherItemType =
  | 'tab'
  | 'action'
  | 'search'
  | 'bookmark'
  | 'history'
  | 'command';

export interface QuickSwitcherItem {
  id: string;
  type: QuickSwitcherItemType;
  title: string;
  subtitle?: string;
  url?: string;
  tabId?: string;
  icon?: string;
  badge?: string;
  action?: () => void;
}

export interface HistoryEntry {
  id: string;
  url: string;
  title: string;
  timestamp: number;
  visitCount?: number;
  favicon?: string;
}

export interface Bookmark {
  id: string;
  url: string;
  title: string;
  folder?: string;
  tags?: string[];
  createdAt: number;
  favicon?: string;
}

export interface Favorite {
  id: string;
  url: string;
  title: string;
  favicon?: string;
}

export type SearchEngine = 'DuckDuckGo' | 'Google' | 'Bing' | 'Ecosia' | 'Kagi';
export type ThemeMode = 'dark' | 'light' | 'sunrise' | 'patron';
export type StartupBehavior = 'newtab' | 'restore';

export interface BrowserSettings {
  searchEngine: SearchEngine;
  theme: ThemeMode;
  blockTrackersAndAds: boolean;
  blockThirdPartyCookies: boolean;
  strictHttps: boolean;
  startupBehavior: StartupBehavior;
  fontFamily?: string;
  natureWallpaper?: boolean;
  wallpaperId?: string;
  mouseGesturesEnabled?: boolean;
  cameraPermission?: 'ask' | 'allow' | 'block';
  microphonePermission?: 'ask' | 'allow' | 'block';
  notificationsPermission?: 'ask' | 'allow' | 'block';
  geolocationPermission?: 'ask' | 'allow' | 'block';
}

export interface AdblockStats {
  totalBlocked: number;
  todayBlocked: number;
  trackersDetected: number;
}

export interface BrowserIPCContextType {
  tabs: Tab[];
  activeTab: Tab | null;
  isQuickSwitcherOpen: boolean;
  isTabSwitcherOpen: boolean;
  isTauriAvailable: boolean;
  isTauriMobileAvailable: boolean;

  // Tabs
  createTab: (url?: string) => Promise<Tab>;
  closeTab: (tabId: string) => Promise<void>;
  closeAllTabs: () => Promise<void>;
  reopenClosedTab: () => Promise<void>;
  switchTab: (tabId: string) => Promise<void>;
  navigate: (tabId: string, url: string) => Promise<void>;
  reloadTab: (tabId: string) => Promise<void>;
  goBack: (tabId: string) => Promise<void>;
  goForward: (tabId: string) => Promise<void>;
  discardTab: (tabId: string) => Promise<boolean>;
  sleepIdleTabs: (threshold?: string) => Promise<string[]>;
  getTabs: () => Promise<Tab[]>;

  // Mobile Navigation (single main WebView on Android/iOS)
  mobileNavigate: (url: string) => Promise<void>;
  mobileReload: () => Promise<void>;
  mobileGoBack: () => Promise<void>;
  mobileGoForward: () => Promise<void>;

  // Find in Page
  findInPage: (query: string, forward: boolean) => Promise<{ match_count: number; current_index: number } | null>;

  // Window Controls
  minimizeWindow: () => Promise<void>;
  maximizeWindow: () => Promise<void>;
  closeWindow: () => Promise<void>;

  // Dialogs / Overlays
  toggleQuickSwitcher: (open?: boolean) => void;
  toggleTabSwitcher: (open?: boolean) => void;
  setViewport: (x: number, y: number, width: number, height: number, hidden: boolean) => Promise<void>;

  // History
  history: HistoryEntry[];
  addHistoryEntry: (entry: { url: string; title: string; favicon?: string }) => void;
  removeHistoryEntry: (id: string) => void;
  clearHistory: () => void;

  // Bookmarks
  bookmarks: Bookmark[];
  addBookmark: (bookmark: Omit<Bookmark, 'id' | 'createdAt'>) => void;
  removeBookmark: (id: string) => void;
  updateBookmark: (id: string, updates: Partial<Bookmark>) => void;

  // Favorites
  favorites: Favorite[];
  addFavorite: (favorite: Omit<Favorite, 'id'>) => void;
  removeFavorite: (id: string) => void;
  updateFavorite: (id: string, updates: Partial<Favorite>) => void;

  // Settings & Adblock
  settings: BrowserSettings;
  updateSettings: (updates: Partial<BrowserSettings>) => void;
  adblockStats: AdblockStats;

  // Site Permissions
  pendingPermissionPrompt?: any;
  setPendingPermissionPrompt?: React.Dispatch<React.SetStateAction<any>>;
  respondToPermission?: (id: string, allow: boolean, remember: boolean) => Promise<void>;
  dismissPermissionPrompt?: () => void;
  triggerTestPermissionPrompt?: (resource: string) => void;

  // Web Notifications
  pendingWebNotification?: any;
  setPendingWebNotification?: React.Dispatch<React.SetStateAction<any>>;
}
