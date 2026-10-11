import React from 'react';
import {
  Plus,
  X,
  Globe,
  RotateCw,
  PanelLeftClose,
  PanelLeftOpen,
  LayoutGrid,
  Moon,
  Sparkles,
} from 'lucide-react';
import { Tab } from '../types/browser';

interface VerticalTabsPaneProps {
  tabs: Tab[];
  activeTab: Tab | null;
  collapsed: boolean;
  onToggleCollapse: () => void;
  onSwitchTab: (tabId: string) => void;
  onCloseTab: (tabId: string) => void;
  onCreateTab: (url?: string) => void;
  onDisableVerticalTabs: () => void;
}

export const VerticalTabsPane: React.FC<VerticalTabsPaneProps> = ({
  tabs,
  activeTab,
  collapsed,
  onToggleCollapse,
  onSwitchTab,
  onCloseTab,
  onCreateTab,
  onDisableVerticalTabs,
}) => {
  return (
    <aside
      aria-label="Vertical Tabs"
      className={`fixed top-3 sm:top-3.5 bottom-3 sm:bottom-4 left-3 sm:left-4 z-40 transition-all duration-200 ease-out flex flex-col select-none font-ui ${
        collapsed ? 'w-[64px]' : 'w-[240px]'
      } bg-[#121212]/95 backdrop-blur-2xl border border-[var(--island-border-adaptive,rgba(255,255,255,0.12))] shadow-[0_18px_50px_rgba(0,0,0,0.7)] rounded-2xl overflow-hidden`}
    >
      {/* HEADER SECTION */}
      <div className="flex items-center justify-between px-3 py-3 border-b border-white/10 shrink-0">
        {!collapsed ? (
          <>
            <div className="flex items-center space-x-2 min-w-0">
              <span className="text-[13px] font-semibold text-white/90 tracking-tight">Tabs</span>
              <span className="text-[10px] font-mono px-1.5 py-0.5 rounded-full bg-white/10 text-[#d4ad66] font-medium">
                {tabs.length}
              </span>
            </div>

            <div className="flex items-center space-x-1">
              {/* Collapse to icon-only mode */}
              <button
                onClick={onToggleCollapse}
                title="Collapse sidebar"
                className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors"
              >
                <PanelLeftClose className="w-4 h-4" />
              </button>

              {/* Disable vertical tabs / switch back to default layout */}
              <button
                onClick={onDisableVerticalTabs}
                title="Disable Vertical Tabs (Switch to Default Layout)"
                className="p-1.5 rounded-lg text-white/60 hover:text-[#d4ad66] hover:bg-white/10 transition-colors"
              >
                <LayoutGrid className="w-4 h-4" />
              </button>
            </div>
          </>
        ) : (
          <div className="w-full flex flex-col items-center gap-1.5">
            <button
              onClick={onToggleCollapse}
              title="Expand sidebar"
              className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors"
            >
              <PanelLeftOpen className="w-4 h-4" />
            </button>
            <span className="text-[10px] font-mono px-1.5 py-0.2 rounded-full bg-white/10 text-[#d4ad66] font-medium">
              {tabs.length}
            </span>
          </div>
        )}
      </div>

      {/* SCROLLABLE TABS LIST */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden p-2 space-y-1.5 scrollbar-thin">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTab?.id;
          const tabUrl = tab.url || '';
          const isInternal = tabUrl.startsWith('blanc://') || tabUrl.startsWith('about:');

          let hostname = 'Blanc';
          try {
            if (tabUrl.startsWith('http')) {
              hostname = new URL(tabUrl).hostname.replace(/^www\./, '');
            } else if (isInternal) {
              hostname = 'System';
            }
          } catch {
            hostname = 'Website';
          }

          if (collapsed) {
            return (
              <div
                key={tab.id}
                onClick={() => onSwitchTab(tab.id)}
                title={`${tab.title || hostname} (${hostname})`}
                className={`relative w-full aspect-square rounded-xl flex items-center justify-center cursor-pointer transition-all group ${
                  isActive
                    ? 'bg-white/15 text-[#d4ad66] border border-[#d4ad66]/50 shadow-sm ring-1 ring-[#d4ad66]/30'
                    : 'text-white/60 hover:text-white hover:bg-white/5 border border-transparent'
                }`}
              >
                {tab.is_loading ? (
                  <RotateCw className="w-4 h-4 animate-spin text-[#d4ad66]" />
                ) : tab.favicon ? (
                  <img
                    src={tab.favicon}
                    alt=""
                    className="w-4 h-4 rounded-sm object-contain"
                    onError={(e) => {
                      (e.target as HTMLElement).style.display = 'none';
                    }}
                  />
                ) : isInternal ? (
                  <Sparkles className="w-4 h-4 text-[#d4ad66]" />
                ) : (
                  <Globe className="w-4 h-4" />
                )}

                {/* Close dot button on hover in collapsed mode */}
                {tabs.length > 1 && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onCloseTab(tab.id);
                    }}
                    title="Close Tab (Ctrl+W)"
                    className="absolute -top-1 -right-1 p-0.5 rounded-full bg-[#1e1e1e] border border-white/20 text-white/70 hover:text-white hover:bg-rose-500/80 opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <X className="w-2.5 h-2.5" />
                  </button>
                )}
              </div>
            );
          }

          return (
            <div
              key={tab.id}
              onClick={() => onSwitchTab(tab.id)}
              className={`flex items-center gap-2.5 px-3 py-2 rounded-xl text-left cursor-pointer transition-all group border ${
                isActive
                  ? 'bg-white/12 text-white border-[#d4ad66]/40 shadow-sm ring-1 ring-[#d4ad66]/20'
                  : 'bg-white/[0.02] text-white/70 hover:text-white hover:bg-white/[0.06] border-white/[0.04]'
              }`}
            >
              {/* Tab Icon */}
              <div className="shrink-0 flex items-center justify-center w-4 h-4">
                {tab.is_loading ? (
                  <RotateCw className="w-3.5 h-3.5 animate-spin text-[#d4ad66]" />
                ) : tab.favicon ? (
                  <img
                    src={tab.favicon}
                    alt=""
                    className="w-3.5 h-3.5 rounded-sm object-contain"
                    onError={(e) => {
                      (e.target as HTMLElement).style.display = 'none';
                    }}
                  />
                ) : isInternal ? (
                  <Sparkles className="w-3.5 h-3.5 text-[#d4ad66]" />
                ) : (
                  <Globe className="w-3.5 h-3.5" />
                )}
              </div>

              {/* Title & Hostname */}
              <div className="min-w-0 flex-1">
                <p className="text-[12.5px] font-medium leading-tight truncate">
                  {tab.title || hostname}
                </p>
                <p className="text-[10px] text-white/40 leading-none truncate mt-0.5">
                  {hostname}
                </p>
              </div>

              {/* State badges (quiet tab / asleep) */}
              {tab.is_asleep && (
                <span title="Tab asleep to save memory">
                  <Moon className="w-3 h-3 text-white/30 shrink-0" />
                </span>
              )}

              {/* Close Button */}
              {tabs.length > 1 && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onCloseTab(tab.id);
                  }}
                  title="Close Tab (Ctrl+W)"
                  className="p-1 rounded-md text-white/30 hover:text-white hover:bg-white/15 opacity-0 group-hover:opacity-100 transition-opacity shrink-0"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </div>
          );
        })}
      </div>

      {/* FOOTER ACTIONS */}
      <div className="p-2 border-t border-white/10 shrink-0 space-y-1.5">
        {/* New Tab Button */}
        <button
          onClick={() => onCreateTab('blanc://newtab')}
          title="New Tab (Ctrl+T)"
          className={`flex items-center justify-center gap-2 rounded-xl transition-all ${
            collapsed
              ? 'w-full aspect-square p-2 bg-white/5 hover:bg-white/10 text-white/80 hover:text-white'
              : 'w-full py-2 px-3 bg-white/5 hover:bg-[#d4ad66]/15 hover:border-[#d4ad66]/30 text-white/85 hover:text-[#d4ad66] border border-white/10 text-[12px] font-medium'
          }`}
        >
          <Plus className="w-4 h-4 shrink-0" />
          {!collapsed && <span>New Tab</span>}
        </button>

        {/* Switch to Default Layout Button */}
        {!collapsed && (
          <button
            onClick={onDisableVerticalTabs}
            title="Switch to default horizontal layout"
            className="w-full flex items-center justify-center gap-1.5 py-1 text-[11px] text-white/40 hover:text-white/80 transition-colors"
          >
            <LayoutGrid className="w-3 h-3" />
            <span>Default Layout</span>
          </button>
        )}
      </div>
    </aside>
  );
};
