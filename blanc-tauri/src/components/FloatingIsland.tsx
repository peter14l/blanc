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
} from 'lucide-react';
import { Tab } from '../types/browser';
import { WindowControls } from './WindowControls';

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
  className = '',
}) => {
  const [inputUrl, setInputUrl] = useState('');
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Synchronize input with active tab URL when not focused
  useEffect(() => {
    if (!isFocused && activeTab) {
      setInputUrl(activeTab.url);
    }
  }, [activeTab, isFocused]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      if (inputUrl.trim()) {
        onNavigate(inputUrl);
        inputRef.current?.blur();
      }
    } else if (e.key === 'Escape') {
      if (activeTab) {
        setInputUrl(activeTab.url);
      }
      inputRef.current?.blur();
    }
  };


  return (
    <div
      data-tauri-drag-region
      className={`relative flex items-center justify-between h-[46px] w-[92%] max-w-[820px] px-3.5 
        bg-slate-900/80 dark:bg-[#161616]/90 backdrop-blur-xl border border-white/10 
        shadow-island-resting dark:shadow-[0_10px_35px_rgba(0,0,0,0.6)] 
        rounded-2xl transition-all duration-200 select-none z-50 ${className}`}
    >
      {/* LEFT SECTION: Window Controls & Nav */}
      <div className="flex items-center space-x-3.5 z-10" data-tauri-drag-region>
        <WindowControls
          onMinimize={onMinimize}
          onMaximize={onMaximize}
          onClose={onClose}
        />

        <div className="h-3.5 w-[1px] bg-white/10 mx-1" />

        <div className="flex items-center space-x-0.5 no-drag">
          <button
            onClick={onGoBack}
            disabled={!activeTab?.can_go_back}
            title="Go Back (Cmd+[)"
            className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 
              disabled:opacity-25 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>

          <button
            onClick={onGoForward}
            disabled={!activeTab?.can_go_forward}
            title="Go Forward (Cmd+])"
            className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 
              disabled:opacity-25 disabled:hover:bg-transparent disabled:hover:text-white/60 transition-colors"
          >
            <ChevronRight className="w-4 h-4" />
          </button>

          <button
            onClick={onReload}
            title="Reload Tab (Cmd+R)"
            className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors"
          >
            <RotateCw
              className={`w-3.5 h-3.5 ${
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
            onNavigate(inputUrl);
            inputRef.current?.blur();
          }
        }}
        className="flex-1 max-w-[420px] mx-3 relative flex items-center h-8 px-2.5 
          bg-white/5 hover:bg-white/8 focus-within:bg-black/40 focus-within:ring-1 focus-within:ring-[#d4ad66]/60 
          rounded-xl border border-white/5 transition-all no-drag group cursor-text"
        onClick={() => inputRef.current?.focus()}
      >
        <div className="mr-2 text-white/40 group-focus-within:text-[#d4ad66] transition-colors flex items-center shrink-0">
          {isFocused ? (
            <Search className="w-3.5 h-3.5" />
          ) : (
            <Globe className="w-3.5 h-3.5" />
          )}
        </div>

        <input
          ref={inputRef}
          type="text"
          value={inputUrl}
          onChange={(e) => setInputUrl(e.target.value)}
          onFocus={() => {
            setIsFocused(true);
            inputRef.current?.select();
          }}
          onBlur={() => setIsFocused(false)}
          onKeyDown={handleKeyDown}
          placeholder={`Search ${searchEngine} or enter URL...`}
          className="w-full bg-transparent text-[13px] text-white/90 placeholder-white/30 
            outline-none border-none tracking-tight font-medium"
        />

        {inputUrl.trim() && isFocused && (
          <button
            type="submit"
            title="Navigate (Enter)"
            className="ml-1 px-2 py-0.5 rounded-md bg-[#d4ad66]/20 text-[#d4ad66] hover:bg-[#d4ad66]/30 text-[11px] font-medium transition-colors shrink-0"
          >
            Go
          </button>
        )}
      </form>

      {/* RIGHT SECTION: Shield & Switchers */}
      <div className="flex items-center space-x-2 z-10 no-drag" data-tauri-drag-region>
        {/* Shield / Tracker counter badge */}
        <div
          onClick={onOpenSettings}
          title={`${activeTab?.blocked_trackers || 0} trackers & ads blocked (Click to open Settings)`}
          className="flex items-center space-x-1.5 px-2.5 py-1 rounded-full 
            bg-[#d4ad66]/10 border border-[#d4ad66]/20 text-[#d4ad66] text-[11px] font-medium 
            hover:bg-[#d4ad66]/15 transition-colors cursor-pointer"
        >
          <ShieldCheck className="w-3.5 h-3.5" />
          <span className="tabular-nums">
            {activeTab?.blocked_trackers || 0}
          </span>
          <span className="hidden sm:inline opacity-80">blocked</span>
        </div>

        {/* Quick Switcher Button */}
        <button
          onClick={onToggleQuickSwitcher}
          title="Quick Switcher (Cmd+K)"
          className="flex items-center space-x-1 px-2 py-1 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors"
        >
          <Command className="w-3.5 h-3.5" />
          <span className="text-[11px] font-medium tracking-tight">K</span>
        </button>

        {/* Tab Switcher Button */}
        <button
          onClick={onToggleTabSwitcher}
          title="Open Tab Switcher"
          className="flex items-center space-x-1 px-2 py-1 rounded-lg text-white/70 hover:text-white hover:bg-white/10 transition-colors"
        >
          <Layers className="w-3.5 h-3.5" />
          <span className="text-[11px] font-semibold bg-white/10 px-1.5 py-0.5 rounded-full">
            {tabsCount}
          </span>
        </button>
      </div>
    </div>
  );
};
