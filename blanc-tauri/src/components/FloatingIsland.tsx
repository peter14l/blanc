import React, { useState, useEffect, useRef } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  RotateCw,
  ShieldCheck,
  Search,
  Globe,
  PanelLeft,
} from 'lucide-react';
import { Tab, HistoryEntry, Bookmark as BookmarkType } from '../types/browser';
import { useSearchSuggestions, SuggestionItem } from '../hooks/useSearchSuggestions';
import { SuggestionPicker } from './SuggestionPicker';

interface FloatingIslandProps {
  activeTab: Tab | null;
  tabsCount?: number;
  searchEngine?: string;
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
  history?: HistoryEntry[];
  bookmarks?: BookmarkType[];
  onSuggestionsOpenChange?: (open: boolean) => void;
  isVerticalTabs?: boolean;
  onToggleVerticalTabs?: () => void;
  className?: string;
}

export const FloatingIsland: React.FC<FloatingIslandProps> = ({
  activeTab,
  searchEngine = 'DuckDuckGo',
  onNavigate,
  onReload,
  onGoBack,
  onGoForward,
  onOpenSettings,
  history = [],
  bookmarks = [],
  onSuggestionsOpenChange,
  isVerticalTabs = false,
  onToggleVerticalTabs,
  className = '',
}) => {
  const [inputUrl, setInputUrl] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const [isUserEditing, setIsUserEditing] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isPickerVisible, setIsPickerVisible] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const activeTabIdRef = useRef<string | null>(null);

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
      className={`relative flex items-center justify-between h-[46px] w-[580px] max-w-[calc(100vw-180px)] px-2.5 rounded-[18px] bg-[#181818]/95 backdrop-blur-xl border ${
        activeTab?.is_private
          ? 'border-dashed border-[#444444]'
          : isFocused
          ? 'border-[#444444] ring-1 ring-white/10'
          : 'border-[#2a2a2a] hover:border-[#383838]'
      } shadow-[0_4px_20px_-4px_rgba(0,0,0,0.5)] transition-all duration-200 select-none z-50 ${className}`}
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
              className="w-full bg-transparent text-[14px] text-white/95 placeholder-white/35 
                outline-none border-none tracking-tight font-medium"
            />

            {/* AUTOCOMPLETE / SUGGESTION PICKER */}
            <SuggestionPicker
              suggestions={suggestions}
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
                <Globe className="w-3.5 h-3.5 text-white/40 shrink-0" />
              )}
              <span className={`text-[14px] truncate tracking-tight ${
                isNewTab ? 'text-white/50' : 'text-white/90 font-medium'
              }`}>
                {isNewTab ? 'new tab' : displayTitle}
              </span>
            </div>
            <span
              className="ml-2 text-[11.5px] font-mono text-white/30 px-1.5 py-0.5 rounded bg-white/[0.04] border border-white/[0.06] shrink-0"
              title="Commands (/ or Ctrl+L)"
            >
              /
            </span>
          </div>
        )}
      </div>

      {/* SUBTLE VERTICAL DIVIDER */}
      <div className="w-px h-3.5 bg-white/10 mx-1.5 shrink-0" />

      {/* 3. RIGHT SECTION: SHIELD BADGE & STATUS */}
      <div className="flex items-center space-x-1.5 shrink-0 no-drag" data-tauri-drag-region>
        {activeTab?.is_private && (
          <span className="px-1.5 py-0.5 rounded text-[11px] uppercase font-mono tracking-wider bg-white/10 text-white/70 shrink-0">
            private
          </span>
        )}

        {/* Shield Tracker Counter Badge */}
        <button
          type="button"
          onClick={onOpenSettings}
          title={`${activeTab?.blocked_trackers || 0} ads & trackers blocked`}
          className="flex items-center space-x-1.5 px-2.5 py-1 rounded-full bg-white/[0.05] hover:bg-white/[0.09] border border-white/10 text-white/80 hover:text-white text-[12px] font-medium transition-colors cursor-pointer shrink-0"
        >
          <ShieldCheck className="w-3.5 h-3.5 text-[#d4ad66]" />
          <span className="tabular-nums font-mono">
            {activeTab?.blocked_trackers || 0}
          </span>
        </button>
      </div>
    </div>
  );
};
