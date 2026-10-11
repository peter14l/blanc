import React, { useEffect, useRef, useLayoutEffect, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  RotateCw,
  Plus,
  Copy,
  ExternalLink,
  Search,
  Scissors,
  Clipboard,
  Code2,
  Printer,
  Settings,
  Layers,
  X,
  Sparkles,
} from 'lucide-react';

export interface ContextMenuProps {
  x: number;
  y: number;
  selectedText?: string;
  linkUrl?: string;
  activeUrl?: string;
  canGoBack?: boolean;
  canGoForward?: boolean;
  onBack?: () => void;
  onForward?: () => void;
  onReload?: () => void;
  onNewTab?: (url?: string) => void;
  onDuplicateTab?: () => void;
  onCloseTab?: () => void;
  onCopyUrl?: (url: string) => void;
  onSearchSelected?: (query: string) => void;
  onViewSource?: () => void;
  onInspect?: () => void;
  onPrint?: () => void;
  onOpenSettings?: () => void;
  onClose: () => void;
}

export const ContextMenu: React.FC<ContextMenuProps> = ({
  x,
  y,
  selectedText = '',
  linkUrl,
  activeUrl = '',
  canGoBack = false,
  canGoForward = false,
  onBack,
  onForward,
  onReload,
  onNewTab,
  onDuplicateTab,
  onCloseTab,
  onCopyUrl,
  onSearchSelected,
  onViewSource,
  onInspect,
  onPrint,
  onOpenSettings,
  onClose,
}) => {
  const menuRef = useRef<HTMLDivElement>(null);
  const [adjustedPos, setAdjustedPos] = useState({ left: x, top: y });

  // Reposition if menu overflows viewport
  useLayoutEffect(() => {
    if (!menuRef.current) return;
    const rect = menuRef.current.getBoundingClientRect();
    const padding = 12;

    let newX = x;
    let newY = y;

    if (x + rect.width > window.innerWidth - padding) {
      newX = Math.max(padding, window.innerWidth - rect.width - padding);
    }
    if (y + rect.height > window.innerHeight - padding) {
      newY = Math.max(padding, window.innerHeight - rect.height - padding);
    }

    setAdjustedPos({ left: newX, top: newY });
  }, [x, y]);

  // Click outside / escape key to dismiss
  useEffect(() => {
    const handleMouseDown = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };

    window.addEventListener('mousedown', handleMouseDown, true);
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('mousedown', handleMouseDown, true);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // Fallback
    }
    onClose();
  };

  const handleCut = async () => {
    document.execCommand('cut');
    onClose();
  };

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      document.execCommand('insertText', false, text);
    } catch {
      document.execCommand('paste');
    }
    onClose();
  };

  const handleSelectAll = () => {
    document.execCommand('selectAll');
    onClose();
  };

  const hasSelection = Boolean(selectedText && selectedText.trim().length > 0);
  const truncatedSelection = hasSelection
    ? selectedText.length > 20
      ? `${selectedText.substring(0, 20)}…`
      : selectedText
    : '';

  return (
    <div
      ref={menuRef}
      style={{
        left: `${adjustedPos.left}px`,
        top: `${adjustedPos.top}px`,
      }}
      className="fixed z-[9999] min-w-[240px] max-w-[320px] rounded-2xl bg-[#141414]/95 backdrop-blur-2xl border border-white/10 shadow-2xl p-1.5 text-white/90 text-[13px] font-medium animate-in fade-in zoom-in-95 duration-100 select-none"
      onClick={(e) => e.stopPropagation()}
      onContextMenu={(e) => e.preventDefault()}
    >
      {/* 1. NAVIGATION QUICK BAR */}
      <div className="flex items-center justify-between px-1 py-1 mb-1 border-b border-white/[0.08]">
        <button
          type="button"
          disabled={!canGoBack}
          onClick={() => {
            onBack?.();
            onClose();
          }}
          title="Back (Alt+Left)"
          className="p-2 rounded-lg hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent transition-colors text-white/80 hover:text-white"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <button
          type="button"
          disabled={!canGoForward}
          onClick={() => {
            onForward?.();
            onClose();
          }}
          title="Forward (Alt+Right)"
          className="p-2 rounded-lg hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-transparent transition-colors text-white/80 hover:text-white"
        >
          <ArrowRight className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => {
            onReload?.();
            onClose();
          }}
          title="Reload (Ctrl+R)"
          className="p-2 rounded-lg hover:bg-white/10 transition-colors text-white/80 hover:text-white"
        >
          <RotateCw className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={() => {
            onNewTab?.();
            onClose();
          }}
          title="New Tab (Ctrl+T)"
          className="p-2 rounded-lg hover:bg-white/10 transition-colors text-white/80 hover:text-white"
        >
          <Plus className="w-4 h-4" />
        </button>
      </div>

      {/* 2. LINK ACTIONS (IF RIGHT-CLICKED A LINK) */}
      {linkUrl && (
        <>
          <div className="py-0.5">
            <button
              type="button"
              onClick={() => {
                onNewTab?.(linkUrl);
                onClose();
              }}
              className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
            >
              <ExternalLink className="w-4 h-4 mr-2.5 text-[#d4ad66] shrink-0" />
              <span className="flex-1 truncate">Open link in new tab</span>
            </button>
            <button
              type="button"
              onClick={() => handleCopy(linkUrl)}
              className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
            >
              <Copy className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
              <span className="flex-1">Copy link address</span>
            </button>
          </div>
          <div className="h-px bg-white/[0.08] my-1" />
        </>
      )}

      {/* 3. SELECTION ACTIONS */}
      {hasSelection ? (
        <>
          <div className="py-0.5">
            <button
              type="button"
              onClick={() => {
                onSearchSelected?.(selectedText);
                onClose();
              }}
              className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
            >
              <Search className="w-4 h-4 mr-2.5 text-[#d4ad66] shrink-0" />
              <span className="flex-1 truncate">Search web for &ldquo;{truncatedSelection}&rdquo;</span>
            </button>
            <button
              type="button"
              onClick={() => handleCopy(selectedText)}
              className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
            >
              <div className="flex items-center min-w-0">
                <Copy className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
                <span>Copy</span>
              </div>
              <span className="text-[11px] font-mono text-white/40">Ctrl+C</span>
            </button>
          </div>
          <div className="h-px bg-white/[0.08] my-1" />
        </>
      ) : (
        /* EDIT ACTIONS */
        <div className="py-0.5">
          <button
            type="button"
            onClick={handleCut}
            className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
          >
            <div className="flex items-center">
              <Scissors className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
              <span>Cut</span>
            </div>
            <span className="text-[11px] font-mono text-white/40">Ctrl+X</span>
          </button>
          <button
            type="button"
            onClick={handlePaste}
            className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
          >
            <div className="flex items-center">
              <Clipboard className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
              <span>Paste</span>
            </div>
            <span className="text-[11px] font-mono text-white/40">Ctrl+V</span>
          </button>
          <button
            type="button"
            onClick={handleSelectAll}
            className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
          >
            <div className="flex items-center">
              <Layers className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
              <span>Select all</span>
            </div>
            <span className="text-[11px] font-mono text-white/40">Ctrl+A</span>
          </button>
        </div>
      )}

      {/* 4. TAB & PAGE ACTIONS */}
      <div className="h-px bg-white/[0.08] my-1" />
      <div className="py-0.5">
        {activeUrl && (
          <button
            type="button"
            onClick={() => {
              if (onCopyUrl) onCopyUrl(activeUrl);
              else handleCopy(activeUrl);
            }}
            className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
          >
            <Copy className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
            <span className="flex-1 truncate">Copy page URL</span>
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            onDuplicateTab?.();
            onClose();
          }}
          className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <Layers className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
          <span className="flex-1">Duplicate tab</span>
        </button>
        <button
          type="button"
          onClick={() => {
            onCloseTab?.();
            onClose();
          }}
          className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <div className="flex items-center">
            <X className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
            <span>Close tab</span>
          </div>
          <span className="text-[11px] font-mono text-white/40">Ctrl+W</span>
        </button>
        <button
          type="button"
          onClick={() => {
            onPrint?.();
            onClose();
          }}
          className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <div className="flex items-center">
            <Printer className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
            <span>Print...</span>
          </div>
          <span className="text-[11px] font-mono text-white/40">Ctrl+P</span>
        </button>
      </div>

      {/* 5. DEVELOPER & SETTINGS */}
      <div className="h-px bg-white/[0.08] my-1" />
      <div className="py-0.5">
        <button
          type="button"
          onClick={() => {
            onViewSource?.();
            onClose();
          }}
          className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <div className="flex items-center">
            <Code2 className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
            <span>View page source</span>
          </div>
          <span className="text-[11px] font-mono text-white/40">Ctrl+U</span>
        </button>
        <button
          type="button"
          onClick={() => {
            onInspect?.();
            onClose();
          }}
          className="w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <div className="flex items-center">
            <Sparkles className="w-4 h-4 mr-2.5 text-[#d4ad66] shrink-0" />
            <span>Inspect element</span>
          </div>
          <span className="text-[11px] font-mono text-white/40">Ctrl+Shift+I</span>
        </button>
        <button
          type="button"
          onClick={() => {
            onOpenSettings?.();
            onClose();
          }}
          className="w-full flex items-center px-2.5 py-1.5 rounded-lg hover:bg-white/10 text-left transition-colors text-white/90 hover:text-white"
        >
          <Settings className="w-4 h-4 mr-2.5 text-white/60 shrink-0" />
          <span className="flex-1">Settings</span>
        </button>
      </div>
    </div>
  );
};
