import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Search,
  Plus,
  ArrowRight,
  ShieldCheck,
  Sparkles,
  Bookmark as BookmarkIcon,
  History,
  Settings,
  Trash2,
  Moon,
  Sun,
  Sunrise,
  Layers,
  Terminal,
} from 'lucide-react';
import {
  Tab,
  Bookmark,
  HistoryEntry,
  BrowserSettings,
  ThemeMode,
  QuickSwitcherItem,
} from '../types/browser';

interface QuickSwitcherProps {
  isOpen: boolean;
  tabs: Tab[];
  bookmarks?: Bookmark[];
  history?: HistoryEntry[];
  settings?: BrowserSettings;
  onSelectTab: (tabId: string) => void;
  onNavigate: (url: string) => void;
  onNewTab: (url?: string) => void;
  onClose: () => void;
  onClearHistory?: () => void;
  onSetTheme?: (theme: ThemeMode) => void;
}

export const QuickSwitcher: React.FC<QuickSwitcherProps> = ({
  isOpen,
  tabs,
  bookmarks = [],
  history = [],
  settings,
  onSelectTab,
  onNavigate,
  onNewTab,
  onClose,
  onClearHistory,
  onSetTheme,
}) => {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);

  // Focus and reset on open
  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  // Construct items
  const items: QuickSwitcherItem[] = useMemo(() => {
    const raw = query.trim();
    const q = raw.toLowerCase();
    const result: QuickSwitcherItem[] = [];

    // 1. SLASH COMMANDS
    const commands: {
      cmd: string;
      title: string;
      desc: string;
      icon: string;
      action: () => void;
    }[] = [
      {
        cmd: '/newtab',
        title: 'New Tab',
        desc: 'Open a blank Blanc start page',
        icon: 'plus',
        action: () => onNewTab('blanc://newtab'),
      },
      {
        cmd: '/history',
        title: 'Browsing History',
        desc: 'Open history viewer (blanc://history)',
        icon: 'history',
        action: () => onNavigate('blanc://history'),
      },
      {
        cmd: '/settings',
        title: 'Settings',
        desc: 'Open browser preferences (blanc://settings)',
        icon: 'settings',
        action: () => onNavigate('blanc://settings'),
      },
      {
        cmd: '/bookmarks',
        title: 'Bookmarks',
        desc: 'Open saved bookmarks (blanc://bookmarks)',
        icon: 'bookmark',
        action: () => onNavigate('blanc://bookmarks'),
      },
      {
        cmd: '/clear',
        title: 'Clear History',
        desc: 'Clear all local browsing history records',
        icon: 'trash',
        action: () => onClearHistory?.(),
      },
      {
        cmd: '/dark',
        title: 'Theme: Dark',
        desc: 'Switch to Obsidian dark palette',
        icon: 'moon',
        action: () => onSetTheme?.('dark'),
      },
      {
        cmd: '/light',
        title: 'Theme: Light',
        desc: 'Switch to Porcelain light palette',
        icon: 'sun',
        action: () => onSetTheme?.('light'),
      },
      {
        cmd: '/sunrise',
        title: 'Theme: Sunrise',
        desc: 'Switch to Warm Gold & Ivory palette',
        icon: 'sunrise',
        action: () => onSetTheme?.('sunrise'),
      },
      {
        cmd: '/patron',
        title: 'Theme: Patron',
        desc: 'Switch to Deep Espresso & Gilded Brass',
        icon: 'sparkles',
        action: () => onSetTheme?.('patron'),
      },
    ];

    if (q.startsWith('/')) {
      const matchCmds = commands.filter((c) => c.cmd.startsWith(q));
      matchCmds.forEach((c) => {
        result.push({
          id: `cmd-${c.cmd}`,
          type: 'command',
          title: c.cmd,
          subtitle: `${c.title} — ${c.desc}`,
          badge: 'Command',
          icon: c.icon,
          action: c.action,
        });
      });
      return result;
    }

    // 2. QUERY-BASED SEARCH AND DIRECT URL
    if (q) {
      const isUrl =
        (q.includes('.') && !q.includes(' ')) ||
        q.startsWith('http://') ||
        q.startsWith('https://') ||
        q.startsWith('blanc://');

      const targetUrl = isUrl
        ? q.startsWith('http') || q.startsWith('blanc://')
          ? q
          : `https://${q}`
        : null;

      if (targetUrl) {
        result.push({
          id: 'action-direct-url',
          type: 'action',
          title: `Go to ${targetUrl}`,
          subtitle: 'Direct Navigation',
          badge: 'URL',
          url: targetUrl,
          action: () => onNavigate(targetUrl),
        });
      }

      // Default Search Engine Search
      const searchEngineName = settings?.searchEngine || 'DuckDuckGo';
      const engineUrl =
        searchEngineName === 'Google'
          ? `https://www.google.com/search?q=${encodeURIComponent(raw)}`
          : searchEngineName === 'Bing'
          ? `https://www.bing.com/search?q=${encodeURIComponent(raw)}`
          : searchEngineName === 'Ecosia'
          ? `https://www.ecosia.org/search?q=${encodeURIComponent(raw)}`
          : searchEngineName === 'Kagi'
          ? `https://kagi.com/search?q=${encodeURIComponent(raw)}`
          : `https://duckduckgo.com/?q=${encodeURIComponent(raw)}`;

      result.push({
        id: 'action-search-default',
        type: 'search',
        title: `Search ${searchEngineName} for "${raw}"`,
        subtitle: `Web Search via ${searchEngineName}`,
        badge: 'Search',
        action: () => onNavigate(engineUrl),
      });

      // Alternative Google Search if not primary
      if (searchEngineName !== 'Google') {
        result.push({
          id: 'action-search-google',
          type: 'search',
          title: `Search Google for "${raw}"`,
          subtitle: 'Web Search via Google',
          badge: 'Google',
          action: () =>
            onNavigate(`https://www.google.com/search?q=${encodeURIComponent(raw)}`),
        });
      }
    }

    // 3. MATCHING OPEN TABS
    const matchingTabs = tabs.filter(
      (t) =>
        t.title.toLowerCase().includes(q) ||
        t.url.toLowerCase().includes(q)
    );

    matchingTabs.forEach((tab) => {
      result.push({
        id: `tab-${tab.id}`,
        type: 'tab',
        title: tab.title || tab.url,
        subtitle: tab.url,
        tabId: tab.id,
        badge: tab.is_active ? 'Active Tab' : 'Open Tab',
        action: () => onSelectTab(tab.id),
      });
    });

    // 4. MATCHING BOOKMARKS
    const matchingBookmarks = bookmarks.filter(
      (b) =>
        b.title.toLowerCase().includes(q) ||
        b.url.toLowerCase().includes(q) ||
        b.tags?.some((t) => t.toLowerCase().includes(q))
    );

    matchingBookmarks.slice(0, 5).forEach((b) => {
      result.push({
        id: `bookmark-${b.id}`,
        type: 'bookmark',
        title: b.title,
        subtitle: `${b.url} ${b.folder ? `• in ${b.folder}` : ''}`,
        url: b.url,
        badge: 'Bookmark',
        action: () => onNavigate(b.url),
      });
    });

    // 5. MATCHING HISTORY ENTRIES
    if (q) {
      const matchingHistory = history.filter(
        (h) =>
          h.title.toLowerCase().includes(q) ||
          h.url.toLowerCase().includes(q)
      );

      matchingHistory.slice(0, 5).forEach((h) => {
        result.push({
          id: `hist-${h.id}`,
          type: 'history',
          title: h.title,
          subtitle: h.url,
          url: h.url,
          badge: 'History',
          action: () => onNavigate(h.url),
        });
      });
    }

    // 6. MATCHING COMMANDS (if user typed keywords like "settings", "history", "clear", etc.)
    if (q) {
      const matchedCmds = commands.filter(
        (c) =>
          c.cmd.includes(q) ||
          c.title.toLowerCase().includes(q) ||
          c.desc.toLowerCase().includes(q)
      );
      matchedCmds.forEach((c) => {
        if (!result.some((r) => r.id === `cmd-${c.cmd}`)) {
          result.push({
            id: `cmd-${c.cmd}`,
            type: 'command',
            title: c.cmd,
            subtitle: `${c.title} — ${c.desc}`,
            badge: 'Command',
            icon: c.icon,
            action: c.action,
          });
        }
      });
    }

    // 7. DEFAULT QUICK ACTIONS WHEN EMPTY QUERY
    if (!q) {
      result.push({
        id: 'action-new-tab',
        type: 'action',
        title: 'New Tab',
        subtitle: 'Cmd+T — Open blank tab',
        badge: 'Action',
        action: () => onNewTab('blanc://newtab'),
      });

      result.push({
        id: 'action-go-history',
        type: 'command',
        title: 'Open Browsing History',
        subtitle: 'blanc://history or type /history',
        badge: 'Page',
        action: () => onNavigate('blanc://history'),
      });

      result.push({
        id: 'action-go-bookmarks',
        type: 'command',
        title: 'Open Bookmarks',
        subtitle: 'blanc://bookmarks or type /bookmarks',
        badge: 'Page',
        action: () => onNavigate('blanc://bookmarks'),
      });

      result.push({
        id: 'action-go-settings',
        type: 'command',
        title: 'Open Settings',
        subtitle: 'blanc://settings or type /settings',
        badge: 'Page',
        action: () => onNavigate('blanc://settings'),
      });

      // Pinned shortcuts
      result.push({
        id: 'bookmark-blanc',
        type: 'bookmark',
        title: 'Blanc Website',
        subtitle: 'https://blancbrowser.com',
        url: 'https://blancbrowser.com',
        badge: 'Pin',
        action: () => onNavigate('https://blancbrowser.com'),
      });
    }

    return result;
  }, [
    query,
    tabs,
    bookmarks,
    history,
    settings,
    onSelectTab,
    onNavigate,
    onNewTab,
    onClearHistory,
    onSetTheme,
  ]);

  // Keep selected index within bounds
  useEffect(() => {
    setSelectedIndex(0);
  }, [items.length]);

  // Scroll active item into view
  useEffect(() => {
    if (itemRefs.current[selectedIndex]) {
      itemRefs.current[selectedIndex]?.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [selectedIndex]);

  // Keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % (items.length || 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + items.length) % (items.length || 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const current = items[selectedIndex];
      if (current?.action) {
        current.action();
        onClose();
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 px-4 bg-black/55 backdrop-blur-md animate-fade-in font-ui">
      <div className="absolute inset-0" onClick={onClose} />

      <div
        className="relative z-50 w-full max-w-xl bg-[#181818]/95 border border-white/10 
          shadow-[0_25px_60px_rgba(0,0,0,0.85)] backdrop-blur-2xl rounded-2xl overflow-hidden animate-slide-down"
      >
        {/* Search Input Bar */}
        <div className="flex items-center px-4 py-3.5 border-b border-white/10">
          <Search className="w-4 h-4 text-white/40 mr-3 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search tabs, history, bookmarks, or type / for commands..."
            className="w-full bg-transparent text-[14px] text-white/95 placeholder-white/30 outline-none border-none font-normal"
          />
          <kbd className="px-1.5 py-0.5 text-[10px] font-medium text-white/40 bg-white/5 border border-white/10 rounded shrink-0">
            ESC
          </kbd>
        </div>

        {/* Results List */}
        <div ref={listRef} className="max-h-[380px] overflow-y-auto p-2 space-y-0.5">
          {items.length === 0 ? (
            <div className="py-10 text-center text-white/30 text-[13px]">
              No results found for "{query}"
            </div>
          ) : (
            items.map((item, idx) => {
              const isSelected = idx === selectedIndex;

              return (
                <div
                  key={item.id}
                  ref={(el) => {
                    itemRefs.current[idx] = el;
                  }}
                  onClick={() => {
                    item.action?.();
                    onClose();
                  }}
                  onMouseEnter={() => setSelectedIndex(idx)}
                  className={`flex items-center justify-between px-3 py-2.5 rounded-xl cursor-pointer transition-colors ${
                    isSelected
                      ? 'bg-white/10 text-white'
                      : 'text-white/70 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  <div className="flex items-center space-x-3 min-w-0 flex-1">
                    <div
                      className={`p-1.5 rounded-lg shrink-0 ${
                        item.type === 'tab'
                          ? 'bg-blue-500/15 text-blue-400'
                          : item.type === 'search'
                          ? 'bg-[#d4ad66]/20 text-[#d4ad66]'
                          : item.type === 'bookmark'
                          ? 'bg-amber-500/15 text-amber-400'
                          : item.type === 'history'
                          ? 'bg-purple-500/15 text-purple-400'
                          : item.type === 'command'
                          ? 'bg-emerald-500/15 text-emerald-400'
                          : 'bg-white/10 text-white/60'
                      }`}
                    >
                      {item.type === 'tab' && <Layers className="w-3.5 h-3.5" />}
                      {item.type === 'search' && <Search className="w-3.5 h-3.5" />}
                      {item.type === 'action' && <Plus className="w-3.5 h-3.5" />}
                      {item.type === 'bookmark' && <BookmarkIcon className="w-3.5 h-3.5" />}
                      {item.type === 'history' && <History className="w-3.5 h-3.5" />}
                      {item.type === 'command' && (
                        <>
                          {item.icon === 'history' && <History className="w-3.5 h-3.5" />}
                          {item.icon === 'settings' && <Settings className="w-3.5 h-3.5" />}
                          {item.icon === 'bookmark' && <BookmarkIcon className="w-3.5 h-3.5" />}
                          {item.icon === 'trash' && <Trash2 className="w-3.5 h-3.5" />}
                          {item.icon === 'moon' && <Moon className="w-3.5 h-3.5" />}
                          {item.icon === 'sun' && <Sun className="w-3.5 h-3.5" />}
                          {item.icon === 'sunrise' && <Sunrise className="w-3.5 h-3.5" />}
                          {item.icon === 'sparkles' && <Sparkles className="w-3.5 h-3.5" />}
                          {item.icon === 'plus' && <Plus className="w-3.5 h-3.5" />}
                          {!item.icon && <Terminal className="w-3.5 h-3.5" />}
                        </>
                      )}
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center space-x-2">
                        <p className="text-[13px] font-medium truncate">{item.title}</p>
                        {item.badge && (
                          <span className="px-1.5 py-0.2 rounded text-[10px] font-medium bg-white/5 border border-white/5 text-white/40 shrink-0">
                            {item.badge}
                          </span>
                        )}
                      </div>
                      {item.subtitle && (
                        <p className="text-[11px] text-white/40 truncate">{item.subtitle}</p>
                      )}
                    </div>
                  </div>

                  {isSelected && (
                    <div className="flex items-center text-white/40 ml-2 shrink-0">
                      <ArrowRight className="w-3.5 h-3.5" />
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>

        {/* Footer shortcuts helper */}
        <div className="flex items-center justify-between px-4 py-2.5 border-t border-white/5 bg-white/[0.02] text-[11px] text-white/40">
          <div className="flex items-center space-x-3">
            <span>↑↓ Navigate</span>
            <span>↵ Select</span>
            <span>ESC Close</span>
          </div>
          <div className="flex items-center space-x-1 text-[#d4ad66]">
            <ShieldCheck className="w-3 h-3" />
            <span>Blanc Blocker Active</span>
          </div>
        </div>
      </div>
    </div>
  );
};
