import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  RotateCw,
  ShieldCheck,
  Search,
  Globe,
  PanelLeft,
} from 'lucide-react';
import { Tab, HistoryEntry, Bookmark as BookmarkType, AdblockStats, ThemeMode } from '../types/browser';
import { useSearchSuggestions, SuggestionItem, SuggestionType } from '../hooks/useSearchSuggestions';
import { SuggestionPicker } from './SuggestionPicker';
import { ShieldFlyout } from './ShieldFlyout';
import { matchSlashCommands, SlashCommand, SlashCommandContext } from '../data/slashCommands';

interface FloatingIslandProps {
  activeTab: Tab | null;
  tabsCount?: number;
  searchEngine?: string;
  adblockStats?: AdblockStats;
  onNavigate: (url: string) => void;
  onReload: () => void;
  onGoBack: () => void;
  onGoForward: () => void;
  onToggleQuickSwitcher?: () => void;
  onToggleTabSwitcher?: () => void;
  onMinimize?: () => void;
  onMaximize?: () => void;
  onClose?: () => void;
  onOpenSettings?: () => void;
  onOpenBookmarks?: () => void;
  onOpenHistory?: () => void;
  onOpenDownloads?: () => void;
  onOpenDiagnostics?: () => void;
  onNewTab?: (url?: string) => void;
  onClearHistory?: () => void;
  onSetTheme?: (theme: ThemeMode) => void;
  history?: HistoryEntry[];
  bookmarks?: BookmarkType[];
  onSuggestionsOpenChange?: (open: boolean) => void;
  onShieldOpenChange?: (open: boolean) => void;
  isVerticalTabs?: boolean;
  onToggleVerticalTabs?: () => void;
  className?: string;
}

export const FloatingIsland: React.FC<FloatingIslandProps> = ({
  activeTab,
  searchEngine = 'DuckDuckGo',
  adblockStats,
  onNavigate,
  onReload,
  onGoBack,
  onGoForward,
  onOpenSettings,
  onOpenDiagnostics,
  onNewTab,
  onClearHistory,
  onSetTheme,
  history = [],
  bookmarks = [],
  onSuggestionsOpenChange,
  onShieldOpenChange,
  isVerticalTabs = false,
  onToggleVerticalTabs,
  className = '',
}) => {
  const [inputUrl, setInputUrl] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const [isUserEditing, setIsUserEditing] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isPickerVisible, setIsPickerVisible] = useState(false);
  const [isShieldOpen, setIsShieldOpen] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const activeTabIdRef = useRef<string | null>(null);

  const isSlashQuery = inputUrl.trim().startsWith('/');

  // Search suggestions are suppressed for `/`-prefixed input; slash commands
  // take over the picker instead so typing `/` in the address bar shows the
  // command list rather than searching for "/".
  const { suggestions } = useSearchSuggestions({
    query: inputUrl,
    history,
    bookmarks,
    enabled: isFocused && !isSlashQuery && inputUrl.trim().length > 0,
  });

  const runSlashCommand = (command: SlashCommand) => {
    const ctx: SlashCommandContext = {
      onNavigate,
      onNewTab: onNewTab || ((url?: string) => onNavigate(url || 'blanc://newtab')),
      onToggleVerticalTabs,
      onClearHistory,
      onSetTheme,
    };
    command.run(ctx);
  };

  // Fallback path: run a `/`-prefixed input as a command even when the picker
  // has not rendered yet (e.g. Enter pressed before the effect settles).
  // Returns true when a command actually ran so callers skip URL navigation.
  const tryRunSlash = (raw: string): boolean => {
    const cmds = matchSlashCommands(raw);
    if (cmds.length === 0) return false;
    runSlashCommand(cmds[0]);
    setIsUserEditing(false);
    setIsPickerVisible(false);
    setIsFocused(false);
    onSuggestionsOpenChange?.(false);
    inputRef.current?.blur();
    return true;
  };

  const commandItems: SuggestionItem[] = useMemo(() => {
    if (!isSlashQuery) return [];
    return matchSlashCommands(inputUrl.trim())
      .slice(0, 8)
      .map((c) => ({
        id: `cmd-${c.cmd}`,
        type: 'command' as SuggestionType,
        title: c.cmd,
        subtitle: `${c.title} — ${c.desc}`,
        badge: 'Command',
        action: () => runSlashCommand(c),
      }));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [inputUrl, isSlashQuery]);

  // When the input is a `/` query the picker shows only commands; otherwise the
  // normal search suggestions. Both share the same selection/Enter flow.
  const displayItems: SuggestionItem[] = isSlashQuery ? commandItems : suggestions;

  useEffect(() => {
    if (isFocused && inputUrl.trim().length > 0 && displayItems.length > 0) {
      setIsPickerVisible(true);
      setSelectedIndex(0);
      onSuggestionsOpenChange?.(true);
    } else {
      setIsPickerVisible(false);
      onSuggestionsOpenChange?.(false);
    }
  }, [displayItems, isFocused, inputUrl, onSuggestionsOpenChange]);

  // Global listener to focus address bar (triggered by Ctrl+L / Alt+D)
  useEffect(() => {
    const handleFocusCommand = () => {
      setIsFocused(true);
      setIsUserEditing(true);
      setTimeout(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      }, 50);
    };
    window.addEventListener('blanc:focus-address-bar', handleFocusCommand);
    return () => window.removeEventListener('blanc:focus-address-bar', handleFocusCommand);
  }, []);

  // Synchronize input with active tab URL
  useEffect(() => {
    if (!activeTab) return;
    const tabUrl = activeTab.url === 'blanc://newtab' ? '' : activeTab.url;

    if (activeTab.id !== activeTabIdRef.current) {
      activeTabIdRef.current = activeTab.id;
      setIsUserEditing(false);
      setIsFocused(false);
      setInputUrl(tabUrl);
      return;
    }

    // On same tab: only synchronize if user is neither focused nor actively editing
    if (!isUserEditing && !isFocused) {
      setInputUrl(tabUrl);
    }
  }, [activeTab?.id, activeTab?.url, isFocused, isUserEditing]);

  const handleSelectSuggestion = (item: SuggestionItem) => {
    setIsPickerVisible(false);
    setIsUserEditing(false);
    setIsFocused(false);
    onSuggestionsOpenChange?.(false);
    if (item.action) {
      item.action();
    } else if (item.url) {
      onNavigate(item.url);
    } else if (item.query) {
      onNavigate(item.query);
    } else {
      onNavigate(item.title);
    }
    inputRef.current?.blur();
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (isPickerVisible && displayItems.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev + 1) % displayItems.length);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev - 1 + displayItems.length) % displayItems.length);
        return;
      }
      if (e.key === 'Enter') {
        e.preventDefault();
        const selected = displayItems[selectedIndex];
        if (selected) {
          handleSelectSuggestion(selected);
          return;
        }
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setIsPickerVisible(false);
        setIsUserEditing(false);
        setIsFocused(false);
        onSuggestionsOpenChange?.(false);
        if (activeTab) {
          setInputUrl(activeTab.url === 'blanc://newtab' ? '' : activeTab.url);
        }
        inputRef.current?.blur();
        return;
      }
    }

    if (e.key === 'Enter') {
      if (inputUrl.trim()) {
        if (tryRunSlash(inputUrl.trim())) return;
        setIsUserEditing(false);
        setIsPickerVisible(false);
        setIsFocused(false);
        onSuggestionsOpenChange?.(false);
        onNavigate(inputUrl);
        inputRef.current?.blur();
      }
    } else if (e.key === 'Escape') {
      setIsPickerVisible(false);
      setIsUserEditing(false);
      setIsFocused(false);
      onSuggestionsOpenChange?.(false);
      if (activeTab) {
        setInputUrl(activeTab.url === 'blanc://newtab' ? '' : activeTab.url);
      }
      inputRef.current?.blur();
    }
  };

  const isNewTab =
    !activeTab ||
    !activeTab.url ||
    activeTab.url === 'blanc://newtab' ||
    activeTab.url.startsWith('about:');

  // Extracted clean hostname or title for collapsed Dynamic Island
  let displayTitle = 'Blanc';
  try {
    if (activeTab?.url && activeTab.url.startsWith('http')) {
      const urlObj = new URL(activeTab.url);
      displayTitle = urlObj.hostname.replace(/^www\./, '');
    } else if (activeTab?.title) {
      displayTitle = activeTab.title;
    }
  } catch {
    displayTitle = activeTab?.title || 'Blanc';
  }

  return (
    <div
      data-tauri-drag-region
      className={`relative flex items-center justify-between h-[46px] w-[580px] max-w-[calc(100vw-180px)] px-2.5 rounded-[18px] bg-[var(--island-resting-surface,#181818)] text-[var(--text,#f5f5f5)] backdrop-blur-xl border ${
        activeTab?.is_private
          ? 'border-dashed border-[var(--border,#444)]'
          : isFocused
          ? 'border-[#d4ad66]/60 ring-1 ring-[#d4ad66]/30'
          : 'border-[var(--border,#2a2a2a)] hover:border-[#d4ad66]/30'
      } shadow-[0_4px_20px_-4px_rgba(0,0,0,0.3)] transition-all duration-200 select-none z-50 ${className}`}
    >
      {/* 1. LEFT NAVIGATION CONTROLS */}
      <div className="flex items-center space-x-0.5 shrink-0 no-drag" data-tauri-drag-region>
        {/* Toggle Vertical Tabs */}
        {onToggleVerticalTabs && (
          <button
            onClick={onToggleVerticalTabs}
            title={isVerticalTabs ? 'Hide vertical tabs (Ctrl+Shift+V)' : 'Show vertical tabs (Ctrl+Shift+V)'}
            className={`w-7 h-7 rounded-lg flex items-center justify-center transition-colors ${
              isVerticalTabs
                ? 'text-[#d4ad66] bg-white/10'
                : 'text-white/70 hover:text-white hover:bg-white/10'
            }`}
          >
            <PanelLeft className="w-4 h-4" />
          </button>
        )}

        <button
          onClick={onGoBack}
          disabled={!activeTab?.can_go_back}
          title="Back (Alt+Left)"
          className="w-7 h-7 rounded-lg flex items-center justify-center text-white/70 hover:text-white hover:bg-white/10 
            disabled:opacity-20 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
        >
          <ChevronLeft className="w-4 h-4" />
        </button>

        <button
          onClick={onGoForward}
          disabled={!activeTab?.can_go_forward}
          title="Forward (Alt+Right)"
          className="w-7 h-7 rounded-lg flex items-center justify-center text-white/70 hover:text-white hover:bg-white/10 
            disabled:opacity-20 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
        >
          <ChevronRight className="w-4 h-4" />
        </button>

        <button
          onClick={onReload}
          title="Reload (Ctrl+R)"
          className="w-7 h-7 rounded-lg flex items-center justify-center text-white/70 hover:text-white hover:bg-white/10 transition-colors"
        >
          <RotateCw
            className={`w-3.5 h-3.5 ${
              activeTab?.is_loading ? 'animate-spin text-[#d4ad66]' : ''
            }`}
          />
        </button>
      </div>

      {/* SUBTLE VERTICAL DIVIDER */}
      <div className="w-px h-3.5 bg-white/10 mx-1.5 shrink-0" />

      {/* 2. CENTER ADDRESS / DOMAIN / SEARCH BAR */}
      <div
        className="flex-1 min-w-0 h-full flex items-center px-1.5 relative no-drag cursor-text"
        onClick={() => {
          if (!isFocused) {
            setIsFocused(true);
            setIsUserEditing(true);
            setTimeout(() => {
              inputRef.current?.focus();
              inputRef.current?.select();
            }, 30);
          }
        }}
      >
        {isFocused ? (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (inputUrl.trim()) {
                if (tryRunSlash(inputUrl.trim())) return;
                setIsUserEditing(false);
                setIsFocused(false);
                onSuggestionsOpenChange?.(false);
                onNavigate(inputUrl);
                inputRef.current?.blur();
              }
            }}
            className="w-full flex items-center h-full relative"
          >
            <Search className="w-3.5 h-3.5 text-white/40 shrink-0 mr-2" />
            <input
              ref={inputRef}
              type="text"
              value={inputUrl}
              onChange={(e) => {
                setInputUrl(e.target.value);
                setIsUserEditing(true);
              }}
              onFocus={() => {
                setIsFocused(true);
                setIsUserEditing(true);
                setTimeout(() => {
                  inputRef.current?.select();
                }, 0);
              }}
              onBlur={() => {
                setTimeout(() => {
                  if (document.activeElement !== inputRef.current) {
                    setIsFocused(false);
                    setIsUserEditing(false);
                    setIsPickerVisible(false);
                    onSuggestionsOpenChange?.(false);
                    if (activeTab) {
                      setInputUrl(activeTab.url === 'blanc://newtab' ? '' : activeTab.url);
                    }
                  }
                }, 200);
              }}
              onKeyDown={handleKeyDown}
              placeholder={`Search ${searchEngine} or enter URL...`}
              className="w-full bg-transparent text-[14px] text-[var(--text,#f5f5f5)] placeholder-[var(--text-dim,#888)] 
                outline-none border-none tracking-tight font-medium"
            />

            {/* AUTOCOMPLETE / SUGGESTION PICKER (search suggestions or slash commands) */}
            <SuggestionPicker
              suggestions={displayItems}
              selectedIndex={selectedIndex}
              onSelect={handleSelectSuggestion}
              onHoverIndex={setSelectedIndex}
              searchEngine={searchEngine}
              visible={isPickerVisible && isFocused}
            />
          </form>
        ) : (
          <div
            className="w-full h-full flex items-center justify-between cursor-pointer"
            title="Search, tabs & commands (Ctrl+L)"
          >
            <div className="flex items-center space-x-2 min-w-0 flex-1">
              {activeTab?.is_loading ? (
                <RotateCw className="w-3.5 h-3.5 animate-spin text-[#d4ad66] shrink-0" />
              ) : activeTab?.favicon ? (
                <img
                  src={activeTab.favicon}
                  alt=""
                  className="w-3.5 h-3.5 rounded-sm object-contain shrink-0"
                  onError={(e) => {
                    e.currentTarget.style.display = 'none';
                  }}
                />
              ) : (
                <Globe className="w-3.5 h-3.5 text-[var(--text-dim,#888)] shrink-0" />
              )}
              <span className={`text-[14px] truncate tracking-tight ${
                isNewTab ? 'text-[var(--text-dim,#888)]' : 'text-[var(--text,#f5f5f5)] font-medium'
              }`}>
                {isNewTab ? 'new tab' : displayTitle}
              </span>
            </div>
            <span
              className="ml-2 text-[11.5px] font-mono text-[var(--text-dim,#888)] px-1.5 py-0.5 rounded bg-[var(--surface)] border border-[var(--border)] shrink-0"
              title="Commands (/ or Ctrl+L)"
            >
              /
            </span>
          </div>
        )}
      </div>

      {/* SUBTLE VERTICAL DIVIDER */}
      <div className="w-px h-3.5 bg-[var(--border)] mx-1.5 shrink-0" />

      {/* 3. RIGHT SECTION: SHIELD BADGE & STATUS */}
      <div className="relative flex items-center space-x-1.5 shrink-0 no-drag" data-tauri-drag-region>
        {activeTab?.is_private && (
          <span className="px-1.5 py-0.5 rounded text-[11px] uppercase font-mono tracking-wider bg-white/10 text-white/70 shrink-0">
            private
          </span>
        )}

        {/* Shield Tracker Counter Badge */}
        <button
          type="button"
          onClick={() => {
            const next = !isShieldOpen;
            setIsShieldOpen(next);
            onShieldOpenChange?.(next);
          }}
          title={`${activeTab?.blocked_trackers || 0} ads & trackers blocked`}
          className={`flex items-center space-x-1.5 px-2.5 py-1 rounded-full border text-[12px] font-medium transition-colors cursor-pointer shrink-0 ${
            isShieldOpen
              ? 'bg-[#d4ad66]/20 border-[#d4ad66]/40 text-[#d4ad66]'
              : 'bg-white/[0.05] hover:bg-white/[0.09] border-white/10 text-white/80 hover:text-white'
          }`}
        >
          <ShieldCheck className="w-3.5 h-3.5 text-[#d4ad66]" />
          <span className="tabular-nums font-mono">
            {activeTab?.blocked_trackers || 0}
          </span>
        </button>

        {/* Shield Flyout Modal */}
        {isShieldOpen && (
          <ShieldFlyout
            isOpen={true}
            activeTab={activeTab}
            adblockStats={adblockStats}
            onClose={() => {
              setIsShieldOpen(false);
              onShieldOpenChange?.(false);
            }}
            onOpenSettings={onOpenSettings}
            onOpenDiagnostics={onOpenDiagnostics}
          />
        )}
      </div>
    </div>
  );
};
