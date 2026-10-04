import React, { useState, useMemo } from 'react';
import {
  Plus,
  X,
  Globe,
  Shield,
  ExternalLink,
  Grid,
  List,
  Search,
  Trash2,
} from 'lucide-react';
import { Tab } from '../types/browser';

interface TabSwitcherProps {
  isOpen: boolean;
  tabs: Tab[];
  activeTabId?: string;
  onSelectTab: (tabId: string) => void;
  onCloseTab: (tabId: string) => void;
  onCloseAllTabs?: () => void;
  onNewTab: () => void;
  onClose: () => void;
}

export const TabSwitcher: React.FC<TabSwitcherProps> = ({
  isOpen,
  tabs,
  activeTabId,
  onSelectTab,
  onCloseTab,
  onCloseAllTabs,
  onNewTab,
  onClose,
}) => {
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [searchFilter, setSearchFilter] = useState('');

  const filteredTabs = useMemo(() => {
    const q = searchFilter.trim().toLowerCase();
    if (!q) return tabs;
    return tabs.filter(
      (t) =>
        t.title.toLowerCase().includes(q) ||
        t.url.toLowerCase().includes(q)
    );
  }, [tabs, searchFilter]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-16 px-4 bg-black/60 backdrop-blur-md animate-fade-in font-ui select-none">
      {/* Click outside backdrop */}
      <div className="absolute inset-0" onClick={onClose} />

      <div
        className="relative z-50 w-full max-w-3xl bg-[#181818]/95 border border-white/10 
          shadow-[0_25px_60px_rgba(0,0,0,0.85)] backdrop-blur-2xl rounded-2xl p-4 sm:p-5 overflow-hidden animate-slide-down flex flex-col max-h-[85vh]"
      >
        {/* Header Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between pb-3 mb-3 border-b border-white/10 gap-2.5">
          <div className="flex items-center space-x-2.5">
            <span className="text-[14px] font-semibold text-white/90">Open Tabs</span>
            <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-white/10 text-white/70">
              {tabs.length}
            </span>
          </div>

          <div className="flex items-center space-x-2">
            {/* View Mode Toggle */}
            <div className="flex items-center bg-white/5 border border-white/10 rounded-xl p-0.5">
              <button
                onClick={() => setViewMode('grid')}
                className={`p-1.5 rounded-lg transition-colors ${
                  viewMode === 'grid' ? 'bg-white/15 text-white' : 'text-white/40 hover:text-white'
                }`}
                title="Grid View"
              >
                <Grid className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={() => setViewMode('list')}
                className={`p-1.5 rounded-lg transition-colors ${
                  viewMode === 'list' ? 'bg-white/15 text-white' : 'text-white/40 hover:text-white'
                }`}
                title="List View"
              >
                <List className="w-3.5 h-3.5" />
              </button>
            </div>

            {/* New Tab Button */}
            <button
              onClick={onNewTab}
              className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] 
                text-[#12100b] text-[12px] font-semibold transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>New Tab</span>
            </button>

            {/* Close All Tabs */}
            {onCloseAllTabs && tabs.length > 1 && (
              <button
                onClick={() => {
                  if (confirm('Close all open tabs?')) {
                    onCloseAllTabs();
                  }
                }}
                className="flex items-center space-x-1 px-2.5 py-1.5 rounded-xl bg-red-500/10 hover:bg-red-500/20 
                  text-red-400 border border-red-500/20 text-[12px] font-medium transition-colors"
                title="Close all tabs"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Close All</span>
              </button>
            )}

            {/* Close Modal */}
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10 transition-colors ml-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Filter Input */}
        <div className="relative mb-3">
          <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
          <input
            type="text"
            value={searchFilter}
            onChange={(e) => setSearchFilter(e.target.value)}
            placeholder="Search open tabs..."
            className="w-full pl-9 pr-8 py-1.5 rounded-xl bg-white/[0.04] hover:bg-white/[0.07] focus:bg-black/40 border border-white/10 focus:border-[#d4ad66]/50 text-white text-[12px] outline-none transition-all placeholder-white/30"
          />
          {searchFilter && (
            <button
              onClick={() => setSearchFilter('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-white/40 hover:text-white p-0.5"
            >
              <X className="w-3 h-3" />
            </button>
          )}
        </div>

        {/* Tab Cards Content */}
        <div className="flex-1 overflow-y-auto pr-1">
          {filteredTabs.length === 0 ? (
            <div className="py-12 text-center text-white/30 text-[13px]">
              No tabs matching "{searchFilter}"
            </div>
          ) : viewMode === 'grid' ? (
            /* GRID VIEW */
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2.5">
              {filteredTabs.map((tab) => {
                const isActive = tab.id === activeTabId || tab.is_active;
                const domain = tab.url.replace(/^https?:\/\//, '').split('/')[0] || tab.url;

                return (
                  <div
                    key={tab.id}
                    onClick={() => {
                      onSelectTab(tab.id);
                      onClose();
                    }}
                    className={`group relative flex flex-col justify-between p-3 rounded-2xl cursor-pointer 
                      border transition-all duration-150 ${
                        isActive
                          ? 'bg-white/10 border-[#d4ad66]/60 shadow-lg ring-1 ring-[#d4ad66]/30'
                          : 'bg-white/[0.03] hover:bg-white/[0.07] border-white/5'
                      }`}
                  >
                    <div className="flex items-start justify-between space-x-2 mb-2">
                      <div className="flex items-center space-x-2 min-w-0">
                        <div
                          className={`p-1.5 rounded-lg shrink-0 ${
                            isActive
                              ? 'bg-[#d4ad66]/20 text-[#d4ad66]'
                              : 'bg-white/5 text-white/60'
                          }`}
                        >
                          <Globe className="w-3.5 h-3.5" />
                        </div>
                        <div className="min-w-0">
                          <p className="text-[13px] font-medium text-white/90 truncate leading-snug">
                            {tab.title || tab.url}
                          </p>
                          <p className="text-[11px] text-white/40 truncate">{domain}</p>
                        </div>
                      </div>

                      {/* Close Tab Button */}
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onCloseTab(tab.id);
                        }}
                        title="Close tab"
                        className="p-1 rounded-md text-white/30 hover:text-white/90 hover:bg-red-500/20 opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>

                    <div className="flex items-center justify-between pt-1.5 border-t border-white/5 text-[11px] text-white/40">
                      <div className="flex items-center space-x-1 text-[#d4ad66]">
                        <Shield className="w-3 h-3" />
                        <span>{tab.blocked_trackers} blocked</span>
                      </div>

                      {isActive ? (
                        <span className="text-[10px] uppercase font-bold text-[#d4ad66] tracking-wider px-1.5 py-0.2 rounded bg-[#d4ad66]/10 border border-[#d4ad66]/20">
                          Active
                        </span>
                      ) : (
                        <span className="flex items-center space-x-0.5 text-white/30 group-hover:text-white/60 transition-colors">
                          <span className="text-[11px]">Switch</span>
                          <ExternalLink className="w-2.5 h-2.5" />
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          ) : (
            /* LIST VIEW */
            <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
              {filteredTabs.map((tab) => {
                const isActive = tab.id === activeTabId || tab.is_active;
                const domain = tab.url.replace(/^https?:\/\//, '').split('/')[0] || tab.url;

                return (
                  <div
                    key={tab.id}
                    onClick={() => {
                      onSelectTab(tab.id);
                      onClose();
                    }}
                    className={`group flex items-center justify-between px-3.5 py-2.5 cursor-pointer transition-colors ${
                      isActive ? 'bg-white/10' : 'hover:bg-white/[0.04]'
                    }`}
                  >
                    <div className="flex items-center space-x-3 min-w-0 flex-1">
                      <div
                        className={`p-1.5 rounded-lg shrink-0 ${
                          isActive
                            ? 'bg-[#d4ad66]/20 text-[#d4ad66]'
                            : 'bg-white/5 text-white/60'
                        }`}
                      >
                        <Globe className="w-3.5 h-3.5" />
                      </div>

                      <div className="min-w-0 flex-1 pr-3">
                        <div className="flex items-center space-x-2">
                          <p
                            className={`text-[13px] font-medium truncate ${
                              isActive ? 'text-[#d4ad66]' : 'text-white/90'
                            }`}
                          >
                            {tab.title || tab.url}
                          </p>
                          {isActive && (
                            <span className="text-[10px] uppercase font-bold text-[#d4ad66] tracking-wider px-1.5 py-0.2 rounded bg-[#d4ad66]/10 border border-[#d4ad66]/20 shrink-0">
                              Active
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-white/40 truncate">{domain}</p>
                      </div>
                    </div>

                    <div className="flex items-center space-x-3 shrink-0">
                      <div className="flex items-center space-x-1 text-[#d4ad66] text-[11px]">
                        <Shield className="w-3 h-3" />
                        <span>{tab.blocked_trackers}</span>
                      </div>

                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onCloseTab(tab.id);
                        }}
                        title="Close tab"
                        className="p-1 rounded-md text-white/30 hover:text-white/90 hover:bg-red-500/20 opacity-0 group-hover:opacity-100 transition-opacity"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
