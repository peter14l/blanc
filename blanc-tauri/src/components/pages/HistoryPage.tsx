import React, { useState, useMemo } from 'react';
import {
  History,
  Search,
  Trash2,
  ExternalLink,
  Copy,
  Check,
  Clock,
  Globe,
  AlertTriangle,
  X,
} from 'lucide-react';
import { HistoryEntry } from '../../types/browser';

interface HistoryPageProps {
  history: HistoryEntry[];
  onNavigate: (url: string) => void;
  onOpenNewTab: (url: string) => void;
  onRemoveEntry: (id: string) => void;
  onClearHistory: () => void;
}

export const HistoryPage: React.FC<HistoryPageProps> = ({
  history,
  onNavigate,
  onOpenNewTab,
  onRemoveEntry,
  onClearHistory,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [isConfirmClearOpen, setIsConfirmClearOpen] = useState(false);

  // Group history chronologically
  const groupedHistory = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    const filtered = history.filter(
      (item) =>
        item.title.toLowerCase().includes(q) || item.url.toLowerCase().includes(q)
    );

    const todayItems: HistoryEntry[] = [];
    const yesterdayItems: HistoryEntry[] = [];
    const earlierItems: HistoryEntry[] = [];

    const now = new Date();
    const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const startOfYesterday = startOfToday - 24 * 60 * 60 * 1000;

    filtered.forEach((item) => {
      if (item.timestamp >= startOfToday) {
        todayItems.push(item);
      } else if (item.timestamp >= startOfYesterday) {
        yesterdayItems.push(item);
      } else {
        earlierItems.push(item);
      }
    });

    return {
      today: todayItems,
      yesterday: yesterdayItems,
      earlier: earlierItems,
      totalCount: filtered.length,
    };
  }, [history, searchQuery]);

  const handleCopyUrl = (id: string, url: string) => {
    navigator.clipboard.writeText(url).then(() => {
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1500);
    });
  };

  const formatTimestamp = (ts: number) => {
    const date = new Date(ts);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const formatDateLabel = (ts: number) => {
    const date = new Date(ts);
    return date.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
  };

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl">
        {/* Header Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between pb-4 mb-6 border-b border-white/10 gap-3">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <History className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-white/95">Browsing History</h1>
              <p className="text-[12px] text-white/40">
                {history.length} {history.length === 1 ? 'entry' : 'entries'} stored locally
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-2 self-end sm:self-auto">
            {history.length > 0 && (
              <button
                onClick={() => setIsConfirmClearOpen(true)}
                className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 text-[12px] font-medium transition-colors"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear All History</span>
              </button>
            )}
          </div>
        </div>

        {/* Search Input */}
        <div className="relative mb-6">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-white/30" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search browsing history by title or web address..."
            className="w-full pl-10 pr-10 py-2.5 rounded-xl bg-white/[0.04] hover:bg-white/[0.07] focus:bg-black/40 border border-white/10 focus:border-[#d4ad66]/50 text-white text-[13px] outline-none transition-all placeholder-white/30"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 p-1 text-white/40 hover:text-white"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* History Group Sections */}
        {groupedHistory.totalCount === 0 ? (
          <div className="py-16 text-center rounded-2xl bg-white/[0.02] border border-white/5 space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-white/5 flex items-center justify-center mx-auto text-white/30">
              <Clock className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <p className="text-[14px] font-medium text-white/80">
                {searchQuery ? 'No matching history found' : 'No browsing history yet'}
              </p>
              <p className="text-[12px] text-white/40">
                {searchQuery
                  ? `No entries matched "${searchQuery}"`
                  : 'Pages you visit while browsing will appear here'}
              </p>
            </div>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Today */}
            {groupedHistory.today.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center space-x-2 text-[12px] font-semibold text-white/50 uppercase tracking-wider px-1">
                  <span>Today</span>
                  <span className="text-white/20">•</span>
                  <span>{groupedHistory.today.length}</span>
                </div>
                <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
                  {groupedHistory.today.map((item) => renderHistoryItem(item))}
                </div>
              </div>
            )}

            {/* Yesterday */}
            {groupedHistory.yesterday.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center space-x-2 text-[12px] font-semibold text-white/50 uppercase tracking-wider px-1">
                  <span>Yesterday</span>
                  <span className="text-white/20">•</span>
                  <span>{groupedHistory.yesterday.length}</span>
                </div>
                <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
                  {groupedHistory.yesterday.map((item) => renderHistoryItem(item))}
                </div>
              </div>
            )}

            {/* Earlier */}
            {groupedHistory.earlier.length > 0 && (
              <div className="space-y-2">
                <div className="flex items-center space-x-2 text-[12px] font-semibold text-white/50 uppercase tracking-wider px-1">
                  <span>Earlier</span>
                  <span className="text-white/20">•</span>
                  <span>{groupedHistory.earlier.length}</span>
                </div>
                <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
                  {groupedHistory.earlier.map((item) => renderHistoryItem(item, true))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Clear History Confirmation Modal */}
      {isConfirmClearOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-sm bg-[#1c1c1c] border border-white/10 rounded-2xl shadow-2xl p-5 animate-slide-down">
            <div className="flex items-center space-x-3 mb-3 text-red-400">
              <div className="p-2 rounded-xl bg-red-500/10 border border-red-500/20">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <h3 className="text-[15px] font-semibold text-white/95">Clear Browsing History?</h3>
            </div>
            <p className="text-[13px] text-white/60 mb-5 leading-relaxed">
              This will permanently delete all {history.length} history records from your local storage. This action cannot be undone.
            </p>
            <div className="flex items-center justify-end space-x-2">
              <button
                onClick={() => setIsConfirmClearOpen(false)}
                className="px-3.5 py-1.5 rounded-xl text-white/60 hover:text-white hover:bg-white/10 text-[12px]"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onClearHistory();
                  setIsConfirmClearOpen(false);
                }}
                className="px-4 py-1.5 rounded-xl bg-red-500 hover:bg-red-600 text-white font-medium text-[12px] transition-colors"
              >
                Clear History
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );

  function renderHistoryItem(item: HistoryEntry, showDate = false) {
    const isCopied = copiedId === item.id;
    const cleanDomain = item.url.replace(/^https?:\/\//, '').split('/')[0];

    return (
      <div
        key={item.id}
        onClick={() => onNavigate(item.url)}
        className="group flex items-center justify-between px-3.5 py-3 hover:bg-white/[0.04] transition-colors cursor-pointer"
      >
        <div className="flex items-center space-x-3 min-w-0 flex-1">
          {/* Favicon or Globe */}
          <div className="w-8 h-8 rounded-lg bg-white/5 border border-white/5 flex items-center justify-center shrink-0 text-white/40">
            {item.favicon ? (
              <img src={item.favicon} alt="" className="w-4 h-4 rounded" />
            ) : (
              <Globe className="w-3.5 h-3.5" />
            )}
          </div>

          {/* Title & URL */}
          <div className="min-w-0 flex-1 pr-3">
            <p className="text-[13px] font-medium text-white/90 truncate group-hover:text-[#d4ad66] transition-colors">
              {item.title || item.url}
            </p>
            <div className="flex items-center space-x-2 text-[11px] text-white/40">
              <span className="truncate max-w-[220px] sm:max-w-md">{item.url}</span>
              <span>•</span>
              <span className="shrink-0">{cleanDomain}</span>
            </div>
          </div>
        </div>

        {/* Timestamp & Actions */}
        <div className="flex items-center space-x-1 shrink-0">
          <span className="text-[11px] text-white/40 font-mono mr-2">
            {showDate ? `${formatDateLabel(item.timestamp)} ` : ''}
            {formatTimestamp(item.timestamp)}
          </span>

          <button
            onClick={(e) => {
              e.stopPropagation();
              onOpenNewTab(item.url);
            }}
            title="Open in New Tab"
            className="p-1.5 rounded-lg text-white/30 hover:text-white hover:bg-white/10 opacity-0 group-hover:opacity-100 transition-all"
          >
            <ExternalLink className="w-3.5 h-3.5" />
          </button>

          <button
            onClick={(e) => {
              e.stopPropagation();
              handleCopyUrl(item.id, item.url);
            }}
            title="Copy URL"
            className="p-1.5 rounded-lg text-white/30 hover:text-white hover:bg-white/10 opacity-0 group-hover:opacity-100 transition-all"
          >
            {isCopied ? (
              <Check className="w-3.5 h-3.5 text-emerald-400" />
            ) : (
              <Copy className="w-3.5 h-3.5" />
            )}
          </button>

          <button
            onClick={(e) => {
              e.stopPropagation();
              onRemoveEntry(item.id);
            }}
            title="Remove from history"
            className="p-1.5 rounded-lg text-white/30 hover:text-red-400 hover:bg-red-500/10 opacity-0 group-hover:opacity-100 transition-all"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    );
  }
};
