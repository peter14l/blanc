import React, { useState, useEffect, useRef } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  RotateCw,
  ShieldCheck,
  Search,
  Globe,
  Layers,
  Command,
  Bookmark,
  History,
  Download,
  Settings,
} from 'lucide-react';
import { Tab, HistoryEntry, Bookmark as BookmarkType } from '../types/browser';
import { WindowControls } from './WindowControls';
import { useSearchSuggestions, SuggestionItem } from '../hooks/useSearchSuggestions';
import { SuggestionPicker } from './SuggestionPicker';

interface FloatingIslandProps {
  activeTab: Tab | null;
  tabsCount: number;
  searchEngine?: string;
  onNavigate: (url: string) => void;
  onReload: () => void;
  onGoBack: () => void;
  onGoForward: () => void;
  onToggleQuickSwitcher: () => void;
  onToggleTabSwitcher: () => void;
  onMinimize: () => void;
  onMaximize: () => void;
  onClose: () => void;
  onOpenSettings?: () => void;
  onOpenBookmarks?: () => void;
  onOpenHistory?: () => void;
  onOpenDownloads?: () => void;
  history?: HistoryEntry[];
  bookmarks?: BookmarkType[];
  onSuggestionsOpenChange?: (open: boolean) => void;
  className?: string;
}

export const FloatingIsland: React.FC<FloatingIslandProps> = ({
  activeTab,
  tabsCount,
  searchEngine = 'DuckDuckGo',
  onNavigate,
  onReload,
  onGoBack,
  onGoForward,
  onToggleQuickSwitcher,
  onToggleTabSwitcher,
  onMinimize,
  onMaximize,
  onClose,
  onOpenSettings,
  onOpenBookmarks,
  onOpenHistory,
  onOpenDownloads,
  history = [],
  bookmarks = [],
  onSuggestionsOpenChange,
  className = '',
}) => {
  const [inputUrl, setInputUrl] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const [isUserEditing, setIsUserEditing] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isPickerVisible, setIsPickerVisible] = useState(false);
  const [isHovered, setIsHovered] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const activeTabIdRef = useRef<string | null>(null);
  const hoverTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const { suggestions } = useSearchSuggestions({
    query: inputUrl,
    history,
    bookmarks,
    enabled: isFocused && inputUrl.trim().length > 0,
  });

  useEffect(() => {
    if (isFocused && inputUrl.trim().length > 0 && suggestions.length > 0) {
      setIsPickerVisible(true);
      setSelectedIndex(0);
      onSuggestionsOpenChange?.(true);
    } else {
      setIsPickerVisible(false);
      onSuggestionsOpenChange?.(false);
    }
  }, [suggestions, isFocused, inputUrl, onSuggestionsOpenChange]);

  // Global listener to focus address bar (triggered by Ctrl+L / Alt+D)
  useEffect(() => {
    const handleFocusCommand = () => {
      setIsHovered(true);
      setIsFocused(true);
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

  const handleMouseEnter = () => {
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
      hoverTimeoutRef.current = null;
    }
    setIsHovered(true);
  };

  const handleMouseLeave = () => {
    if (hoverTimeoutRef.current) {
      clearTimeout(hoverTimeoutRef.current);
    }
    hoverTimeoutRef.current = setTimeout(() => {
      setIsHovered(false);
    }, 280);
  };

  useEffect(() => {
    return () => {
      if (hoverTimeoutRef.current) {
        clearTimeout(hoverTimeoutRef.current);
      }
    };
  }, []);

  const handleSelectSuggestion = (item: SuggestionItem) => {
    setIsPickerVisible(false);
    setIsUserEditing(false);
    setIsFocused(false);
    onSuggestionsOpenChange?.(false);
    if (item.url) {
      onNavigate(item.url);
    } else if (item.query) {
      onNavigate(item.query);
    } else {
      onNavigate(item.title);
    }
    inputRef.current?.blur();
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

  const isMac =
    typeof navigator !== 'undefined' &&
    !/Windows|Win32|Win64|Linux|Android/i.test(navigator.userAgent || '') &&
    (/Macintosh|Mac OS X|macOS/i.test(navigator.userAgent || '') ||
      (navigator as any).userAgentData?.platform === 'macOS' ||
      /MacIntel|MacPPC|Mac68K/i.test((navigator as any).platform || ''));

  // Collapsed vs Expanded Island logic
  const isNewTab =
    !activeTab ||
    !activeTab.url ||
    activeTab.url === 'blanc://newtab' ||
    activeTab.url.startsWith('about:');

  const isExpanded = isNewTab || isHovered || isFocused || isPickerVisible || isUserEditing;

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
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      className={`relative flex items-center justify-between transition-all duration-300 ease-[cubic-bezier(0.16,1,0.3,1)] select-none z-50 ${
        isExpanded
          ? 'h-[52px] w-[94%] max-w-[890px] px-4 rounded-2xl bg-slate-900/80 dark:bg-[#161616]/95 backdrop-blur-xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.1))] shadow-island-resting dark:shadow-[0_12px_40px_rgba(0,0,0,0.65)]'
          : 'h-[40px] w-[270px] px-3.5 rounded-full bg-[#111111]/95 backdrop-blur-2xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.15))] shadow-[0_8px_30px_rgba(0,0,0,0.65)] cursor-pointer hover:border-[#d4ad66]/40 hover:scale-[1.02]'
      } ${className}`}
    >
      {/* 1. COLLAPSED DYNAMIC ISLAND VIEW */}
      {!isExpanded && (
        <div
          onClick={() => {
            setIsHovered(true);
            setIsFocused(true);
            setTimeout(() => {
              inputRef.current?.focus();
              inputRef.current?.select();
            }, 60);
          }}
          className="flex items-center justify-between w-full h-full cursor-pointer animate-fadeIn"
          data-tauri-drag-region
        >
          {/* Left: Favicon or Loading Spinner */}
          <div className="flex items-center space-x-2 shrink-0">
            {activeTab?.is_loading ? (
              <RotateCw className="w-3.5 h-3.5 animate-spin text-[#d4ad66]" />
            ) : activeTab?.favicon ? (
              <img
                src={activeTab.favicon}
                alt=""
                className="w-3.5 h-3.5 rounded-sm object-contain"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                }}
              />
            ) : (
              <Globe className="w-3.5 h-3.5 text-white/50" />
            )}
          </div>

          {/* Center: Truncated Title / Hostname */}
          <div className="flex-1 min-w-0 text-center px-2">
            <span className="text-[12.5px] font-medium text-white/90 truncate block tracking-tight">
              {displayTitle}
            </span>
          </div>

          {/* Right: Shield Tracker Count Badge */}
          <div className="flex items-center space-x-1.5 shrink-0">
            <div
              title={`${activeTab?.blocked_trackers || 0} trackers & ads blocked`}
              className="flex items-center space-x-1 px-1.5 py-0.5 rounded-full bg-[#d4ad66]/15 text-[#d4ad66] text-[11px] font-medium"
            >
              <ShieldCheck className="w-3 h-3" />
              <span className="tabular-nums">{activeTab?.blocked_trackers || 0}</span>
            </div>
          </div>
        </div>
      )}

      {/* 2. EXPANDED FULL ISLAND CONTROLS */}
      {isExpanded && (
        <div className="flex items-center justify-between w-full h-full animate-fadeIn">
          {/* LEFT SECTION: Window Controls & Nav */}
          <div className="flex items-center space-x-3.5 z-10" data-tauri-drag-region>
            {isMac && (
              <>
                <WindowControls
                  onMinimize={onMinimize}
                  onMaximize={onMaximize}
                  onClose={onClose}
                />
                <div className="h-4 w-[1px] bg-white/10 mx-1" />
              </>
            )}

            <div className="flex items-center space-x-1 no-drag">
              <button
                onClick={onGoBack}
                disabled={!activeTab?.can_go_back}
                title="Go Back (Cmd+[)"
                className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 
                  disabled:opacity-25 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
              >
                <ChevronLeft className="w-4.5 h-4.5" />
              </button>

              <button
                onClick={onGoForward}
                disabled={!activeTab?.can_go_forward}
                title="Go Forward (Cmd+])"
                className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 
                  disabled:opacity-25 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
              >
                <ChevronRight className="w-4.5 h-4.5" />
              </button>

              <button
                onClick={onReload}
                title="Reload Tab (Cmd+R)"
                className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
              >
                <RotateCw
                  className={`w-4 h-4 ${
                    activeTab?.is_loading ? 'animate-spin text-[#d4ad66]' : ''
                  }`}
                />
              </button>
            </div>
          </div>

          {/* CENTER SECTION: URL / Search Input */}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (inputUrl.trim()) {
                setIsUserEditing(false);
                setIsFocused(false);
                onSuggestionsOpenChange?.(false);
                onNavigate(inputUrl);
                inputRef.current?.blur();
              }
            }}
            className="flex-1 max-w-[480px] lg:max-w-[520px] mx-3 relative flex items-center h-10 px-3.5 
              bg-white/5 hover:bg-white/8 focus-within:bg-black/50 focus-within:ring-1 focus-within:ring-[#d4ad66]/60 
              rounded-xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.1))] transition-all no-drag group cursor-text"
            onClick={() => {
              if (document.activeElement !== inputRef.current) {
                inputRef.current?.focus();
              }
            }}
          >
            <div className="mr-2.5 text-white/45 group-focus-within:text-[#d4ad66] transition-colors flex items-center shrink-0">
              {isFocused ? (
                <Search className="w-4 h-4" />
              ) : (
                <Globe className="w-4 h-4" />
              )}
            </div>

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
                }, 250);
              }}
              onKeyDown={handleKeyDown}
              placeholder={`Search ${searchEngine} or enter URL...`}
              className="w-full bg-transparent text-[14px] text-white/95 placeholder-white/35 
                outline-none border-none tracking-tight font-medium"
            />

            {inputUrl.trim() && isFocused && (
              <button
                type="submit"
                title="Navigate (Enter)"
                className="ml-1.5 px-2.5 py-1 rounded-lg bg-[#d4ad66]/20 text-[#d4ad66] hover:bg-[#d4ad66]/30 text-[12px] font-semibold transition-colors shrink-0"
              >
                Go
              </button>
            )}

            {/* SMART AUTOCOMPLETE / SUGGESTION PICKER */}
            <SuggestionPicker
              suggestions={suggestions}
              selectedIndex={selectedIndex}
              onSelect={handleSelectSuggestion}
              onHoverIndex={setSelectedIndex}
              searchEngine={searchEngine}
              visible={isPickerVisible && isFocused}
            />
          </form>

          {/* RIGHT SECTION: Shield, Bookmarks, History, Downloads, Switchers, Settings */}
          <div className="flex items-center space-x-1.5 sm:space-x-2 z-10 no-drag" data-tauri-drag-region>
            {/* Shield / Tracker counter badge */}
            <div
              onClick={onOpenSettings}
              title={`${activeTab?.blocked_trackers || 0} trackers & ads blocked (Click to open Settings)`}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-full 
                bg-[#d4ad66]/10 border border-[#d4ad66]/20 text-[#d4ad66] text-[12px] font-medium 
                hover:bg-[#d4ad66]/15 transition-colors cursor-pointer"
            >
              <ShieldCheck className="w-4 h-4" />
              <span className="tabular-nums">
                {activeTab?.blocked_trackers || 0}
              </span>
              <span className="hidden md:inline opacity-80">blocked</span>
            </div>

            {/* Bookmarks Button */}
            <button
              onClick={onOpenBookmarks}
              title="Bookmarks (Cmd+Shift+B)"
              className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            >
              <Bookmark className="w-4 h-4" />
            </button>

            {/* History Button */}
            <button
              onClick={onOpenHistory}
              title="History (Cmd+Y)"
              className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            >
              <History className="w-4 h-4" />
            </button>

            {/* Downloads Button */}
            <button
              onClick={onOpenDownloads}
              title="Downloads (Ctrl+J)"
              className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            >
              <Download className="w-4 h-4" />
            </button>

            {/* Quick Switcher Button */}
            <button
              onClick={onToggleQuickSwitcher}
              title="Quick Switcher (Cmd+K)"
              className="flex items-center space-x-1.5 px-2.5 py-1.5 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            >
              <Command className="w-4 h-4" />
              <span className="text-[12px] font-medium tracking-tight">K</span>
            </button>

            {/* Tab Switcher Button */}
            <button
              onClick={onToggleTabSwitcher}
              title="Open Tab Switcher"
              className="flex items-center space-x-1.5 px-2.5 py-1.5 rounded-xl text-white/75 hover:text-white hover:bg-white/10 transition-colors"
            >
              <Layers className="w-4 h-4" />
              <span className="text-[12px] font-semibold bg-white/10 px-2 py-0.5 rounded-full">
                {tabsCount}
              </span>
            </button>

            {/* Settings Button */}
            <button
              onClick={onOpenSettings}
              title="Settings (Cmd+,)"
              className="p-2 rounded-xl text-white/70 hover:text-white hover:bg-white/10 transition-colors"
            >
              <Settings className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
