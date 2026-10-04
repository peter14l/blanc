import React from 'react';
import {
  Globe,
  Search,
  Bookmark,
  History,
  CornerDownLeft,
  Compass,
} from 'lucide-react';
import { SuggestionItem } from '../hooks/useSearchSuggestions';

interface SuggestionPickerProps {
  suggestions: SuggestionItem[];
  selectedIndex: number;
  onSelect: (item: SuggestionItem) => void;
  onHoverIndex: (index: number) => void;
  searchEngine?: string;
  visible: boolean;
  className?: string;
}

export const SuggestionPicker: React.FC<SuggestionPickerProps> = ({
  suggestions,
  selectedIndex,
  onSelect,
  onHoverIndex,
  searchEngine = 'DuckDuckGo',
  visible,
  className = '',
}) => {
  if (!visible || suggestions.length === 0) return null;

  return (
    <div
      role="listbox"
      aria-label="Address suggestions"
      style={{
        backgroundColor: 'var(--surface-raised)',
      }}
      className={`absolute left-0 right-0 top-full mt-1.5 overflow-hidden rounded-[var(--radius-lg)]
        border border-[var(--border)] text-[var(--text)]
        shadow-[0_18px_42px_rgba(0,0,0,0.28)] backdrop-blur-xl
        animate-in fade-in slide-in-from-top-1 duration-150 select-none ${className}`}
      onMouseDown={(e) => {
        // Prevent input from blurring when clicking inside picker
        e.preventDefault();
      }}
    >
      <div className="max-h-[360px] overflow-y-auto p-1 custom-scrollbar">
        {suggestions.map((item, index) => {
          const isSelected = index === selectedIndex;
          const isQuery = item.type === 'query';

          return (
            <div
              key={item.id}
              role="option"
              aria-selected={isSelected}
              onClick={() => onSelect(item)}
              onMouseEnter={() => onHoverIndex(index)}
              className={`flex min-h-10 items-center gap-3 rounded-[var(--radius-sm)] px-2.5 py-2
                cursor-pointer transition-colors text-left ${
                isSelected
                  ? 'bg-[var(--accent-dim)] text-[var(--text)]'
                  : 'text-[var(--text)] hover:bg-[var(--accent-dim)]'
              }`}
            >
              <div className="flex min-w-0 flex-1 items-center gap-3">
                <div
                  className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md
                    border border-[var(--border)] bg-[var(--surface)] text-[var(--text-dim)] ${
                    isSelected ? 'border-[var(--accent)]/30 text-[var(--accent)]' : ''
                  }`}
                >
                  {item.type === 'website' ? (
                    <Globe className="w-4 h-4" />
                  ) : item.type === 'bookmark' ? (
                    <Bookmark className="w-4 h-4" />
                  ) : item.type === 'history' ? (
                    <History className="w-4 h-4" />
                  ) : item.type === 'internal' ? (
                    <Compass className="w-4 h-4" />
                  ) : (
                    <Search className="w-4 h-4" />
                  )}
                </div>

                <div className="min-w-0 flex-1 leading-tight">
                  <div className="flex min-w-0 items-center gap-2">
                    <span className="truncate text-[13px] font-medium">
                      {item.title}
                    </span>
                    {!isQuery && item.badge && item.badge !== 'Suggested Website' && (
                      <span className="shrink-0 text-[10px] text-[var(--text-dim)]">
                        {item.badge}
                      </span>
                    )}
                  </div>
                  <div className="mt-0.5 truncate text-[11px] text-[var(--text-dim)]">
                    {item.subtitle || (isQuery ? `Search with ${searchEngine}` : '')}
                  </div>
                </div>
              </div>

              <div className="flex h-6 w-6 shrink-0 items-center justify-center text-[var(--accent)]">
                {isSelected && <CornerDownLeft className="h-3.5 w-3.5" />}
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex items-center justify-between gap-3 border-t border-[var(--border)] px-3 py-2 text-[10px] text-[var(--text-dim)]">
        <div className="flex items-center gap-3">
          <span><kbd>↑↓</kbd> Navigate</span>
          <span><kbd>↵</kbd> Open</span>
          <span><kbd>Esc</kbd> Close</span>
        </div>
        <span className="hidden truncate sm:inline">Search with {searchEngine}</span>
      </div>
    </div>
  );
};
