import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Search, ChevronUp, ChevronDown, X } from 'lucide-react';
import { useBrowserIPC } from '../hooks/useBrowserIPC';

interface FindCapsuleProps {
  isOpen: boolean;
  onClose: () => void;
}

export const FindCapsule: React.FC<FindCapsuleProps> = ({ isOpen, onClose }) => {
  const [query, setQuery] = useState('');
  const [matchCount, setMatchCount] = useState<number>(0);
  const [currentIndex, setCurrentIndex] = useState<number>(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const { findInPage } = useBrowserIPC();

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => {
        inputRef.current?.focus();
        inputRef.current?.select();
      }, 50);
    } else {
      setQuery('');
      setMatchCount(0);
      setCurrentIndex(0);
    }
  }, [isOpen]);

  const handleFind = useCallback(async (forward: boolean) => {
    if (!query) return;
    if (!findInPage) return;
    
    try {
      const result = await findInPage(query, forward);
      if (result) {
        setMatchCount(result.match_count);
        setCurrentIndex(result.current_index);
      }
    } catch (err) {
      console.warn('[FindCapsule] Find in page failed:', err);
    }
  }, [query, findInPage]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    } else if (e.key === 'Enter') {
      e.preventDefault();
      handleFind(!e.shiftKey);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed top-4 right-6 z-50 animate-in fade-in slide-in-from-top-2 duration-200">
      <div className="flex items-center space-x-1.5 px-3 py-1.5 rounded-full bg-[#181818]/90 border border-white/15 shadow-2xl backdrop-blur-xl text-white font-ui text-[13px]">
        <Search className="w-3.5 h-3.5 text-white/40 shrink-0 ml-0.5" />
        <input
          ref={inputRef}
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Find in page"
          className="w-36 bg-transparent text-white placeholder-white/30 text-[13px] outline-none border-none py-1"
        />

        {query && (
          <span className="text-[11px] font-mono text-white/50 px-1.5 shrink-0">
            {matchCount > 0 ? `${currentIndex}/${matchCount}` : 'No matches'}
          </span>
        )}

        <div className="h-4 w-[1px] bg-white/10 shrink-0" />

        <button
          onClick={() => handleFind(false)}
          title="Previous match (Shift+Enter)"
          className="p-1 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
        >
          <ChevronUp className="w-3.5 h-3.5" />
        </button>

        <button
          onClick={() => handleFind(true)}
          title="Next match (Enter)"
          className="p-1 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
        >
          <ChevronDown className="w-3.5 h-3.5" />
        </button>

        <button
          onClick={onClose}
          title="Close (Esc)"
          className="p-1 rounded-full hover:bg-white/10 text-white/60 hover:text-white transition-colors"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  );
};