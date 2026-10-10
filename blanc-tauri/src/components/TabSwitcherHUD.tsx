import React, { useEffect, useRef } from 'react';
import { Tab } from '../types/browser';
import { Globe, ShieldCheck, RotateCw } from 'lucide-react';

interface TabSwitcherHUDProps {
  isOpen: boolean;
  tabs: Tab[];
  selectedIndex: number;
  onSelectIndex: (index: number) => void;
  onConfirm: (tabId: string) => void;
  onClose: () => void;
}

export const TabSwitcherHUD: React.FC<TabSwitcherHUDProps> = ({
  isOpen,
  tabs,
  selectedIndex,
  onSelectIndex,
  onConfirm,
  onClose,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);

  // Auto-scroll selected card into view if overflowing
  useEffect(() => {
    if (isOpen && containerRef.current) {
      const selectedEl = containerRef.current.querySelector(`[data-index="${selectedIndex}"]`);
      if (selectedEl) {
        selectedEl.scrollIntoView({
          behavior: 'smooth',
          block: 'nearest',
          inline: 'center',
        });
      }
    }
  }, [isOpen, selectedIndex]);

  if (!isOpen || tabs.length === 0) return null;

  return (
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-md animate-fadeIn select-none font-ui"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative max-w-4xl w-[92%] p-5 rounded-3xl bg-[#121212]/95 border border-[var(--island-border-adaptive,rgba(255,255,255,0.18))] 
          shadow-[0_24px_70px_rgba(0,0,0,0.85)] flex flex-col gap-4 overflow-hidden"
      >
        {/* Top Header & Keyboard instructions */}
        <div className="flex items-center justify-between pb-1 border-b border-white/10">
          <div className="flex items-center space-x-2.5">
            <h3 className="text-[14px] font-semibold text-white/95 tracking-tight">Open Tabs</h3>
            <span className="text-[11px] font-mono px-2 py-0.5 rounded-full bg-white/10 text-white/70">
              {selectedIndex + 1} of {tabs.length}
            </span>
          </div>

          <div className="flex items-center space-x-2 text-[11px] text-white/45 font-medium">
            <span>Hold</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10px]">
              Alt
            </kbd>
            <span>or</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10px]">
              Ctrl
            </kbd>
            <span>+</span>
            <kbd className="px-1.5 py-0.5 rounded-md bg-white/10 text-white/80 font-mono text-[10px]">
              Tab
            </kbd>
            <span>• Release to switch</span>
          </div>
        </div>

        {/* Tab Cards Strip */}
        <div
          ref={containerRef}
          className="flex items-center gap-3 overflow-x-auto py-2 px-1 scrollbar-none"
        >
          {tabs.map((tab, idx) => {
            const isSelected = idx === selectedIndex;
            const isInternal = tab.url.startsWith('blanc://') || tab.url.startsWith('about:');

            let displayDomain = 'Blanc';
            try {
              if (tab.url && tab.url.startsWith('http')) {
                displayDomain = new URL(tab.url).hostname.replace(/^www\./, '');
              } else if (isInternal) {
                displayDomain = 'System';
              }
            } catch {
              displayDomain = 'Website';
            }

            return (
              <div
                key={tab.id}
                data-index={idx}
                onMouseEnter={() => onSelectIndex(idx)}
                onClick={() => onConfirm(tab.id)}
                className={`relative flex flex-col justify-between p-3.5 rounded-2xl border transition-all duration-200 cursor-pointer 
                  shrink-0 w-[200px] h-[120px] ${
                    isSelected
                      ? 'border-[#d4ad66] bg-[#d4ad66]/15 scale-[1.03] shadow-[0_8px_30px_rgba(212,173,102,0.3)] ring-2 ring-[#d4ad66]/80'
                      : 'border-white/10 bg-white/5 hover:bg-white/10 opacity-70 hover:opacity-100'
                  }`}
              >
                {/* Card Top: Favicon + Badges */}
                <div className="flex items-center justify-between w-full">
                  <div className="flex items-center space-x-2 min-w-0">
                    {tab.is_loading ? (
                      <RotateCw className="w-4 h-4 animate-spin text-[#d4ad66]" />
                    ) : tab.favicon ? (
                      <img
                        src={tab.favicon}
                        alt=""
                        className="w-4 h-4 rounded-sm object-contain"
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                    ) : (
                      <Globe className="w-4 h-4 text-white/50" />
                    )}
                    <span className="text-[11px] font-mono text-white/60 truncate max-w-[100px]">
                      {displayDomain}
                    </span>
                  </div>

                  {tab.blocked_trackers > 0 ? (
                    <div
                      title={`${tab.blocked_trackers} trackers blocked`}
                      className="flex items-center space-x-1 px-1.5 py-0.5 rounded-full bg-[#d4ad66]/20 text-[#d4ad66] text-[10px] font-semibold"
                    >
                      <ShieldCheck className="w-2.5 h-2.5" />
                      <span>{tab.blocked_trackers}</span>
                    </div>
                  ) : (
                    <span className="text-[10px] font-mono text-white/30">#{idx + 1}</span>
                  )}
                </div>

                {/* Card Bottom: Title & URL */}
                <div className="flex flex-col min-w-0 mt-auto">
                  <span
                    className={`text-[13px] font-semibold truncate leading-tight transition-colors ${
                      isSelected ? 'text-[#d4ad66]' : 'text-white/95'
                    }`}
                  >
                    {tab.title || 'Untitled'}
                  </span>
                  <span className="text-[11px] text-white/40 truncate mt-0.5 font-mono">
                    {isInternal ? tab.url : tab.url.replace(/^https?:\/\//, '')}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
