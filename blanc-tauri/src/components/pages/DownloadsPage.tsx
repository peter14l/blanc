import React, { useState, useEffect, useCallback } from 'react';
import {
  Download,
  FolderOpen,
  Play,
  XCircle,
  Trash2,
  Search,
  CheckCircle2,
  AlertCircle,
  Clock,
  RefreshCw,
  FileCode,
  FileText,
  FileArchive,
  File,
} from 'lucide-react';

export type DownloadState = 'pending' | 'inProgress' | 'completed' | 'cancelled' | 'failed';

export interface DownloadItem {
  id: string;
  windowId?: string;
  tabId?: string;
  url: string;
  fileName: string;
  state: DownloadState;
  receivedBytes: number;
  totalBytes?: number | null;
  targetPath: string;
  error?: string | null;
  isPrivate?: boolean;
  profileId?: string;
  startedAt: number;
  completedAt?: number | null;
}

interface DownloadsPageProps {
  onNavigate?: (url: string) => void;
}

const MOCK_DOWNLOADS: DownloadItem[] = [
  {
    id: 'dl-1',
    url: 'https://github.com/bnfy/blanc/releases/download/v1.27.0/Blanc-1.27.0-arm64.dmg',
    fileName: 'Blanc-1.27.0-arm64.dmg',
    state: 'completed',
    receivedBytes: 94371840,
    totalBytes: 94371840,
    targetPath: '~/Downloads/Blanc-1.27.0-arm64.dmg',
    startedAt: Date.now() - 3600000 * 2,
    completedAt: Date.now() - 3600000 * 2 + 15000,
  },
  {
    id: 'dl-2',
    url: 'https://easylist.to/easylist/easylist.txt',
    fileName: 'easylist.txt',
    state: 'completed',
    receivedBytes: 2457600,
    totalBytes: 2457600,
    targetPath: '~/Downloads/easylist.txt',
    startedAt: Date.now() - 86400000,
    completedAt: Date.now() - 86400000 + 2000,
  },
];

export const DownloadsPage: React.FC<DownloadsPageProps> = () => {
  const [downloads, setDownloads] = useState<DownloadItem[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const isTauri =
    typeof window !== 'undefined' &&
    ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

  const fetchDownloads = useCallback(async () => {
    setIsLoading(true);
    if (isTauri) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        const list = await invoke<DownloadItem[]>('downloads_list');
        if (list && list.length > 0) {
          setDownloads(list);
        } else {
          setDownloads([]);
        }
      } catch (err) {
        console.warn('[DownloadsPage] downloads_list failed, falling back:', err);
        setDownloads(MOCK_DOWNLOADS);
      }
    } else {
      setDownloads(MOCK_DOWNLOADS);
    }
    setIsLoading(false);
  }, [isTauri]);

  useEffect(() => {
    fetchDownloads();
    // Poll every 3 seconds if there are active downloads
    const interval = setInterval(fetchDownloads, 3000);
    return () => clearInterval(interval);
  }, [fetchDownloads]);

  const handleOpen = async (id: string) => {
    if (isTauri) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        await invoke('downloads_open', { id });
      } catch (err) {
        console.warn('[DownloadsPage] downloads_open error:', err);
      }
    }
  };

  const handleShowInFolder = async (id: string) => {
    if (isTauri) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        await invoke('downloads_show_in_folder', { id });
      } catch (err) {
        console.warn('[DownloadsPage] downloads_show_in_folder error:', err);
      }
    }
  };

  const handleCancel = async (id: string) => {
    if (isTauri) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        await invoke('downloads_cancel', { id });
        fetchDownloads();
      } catch (err) {
        console.warn('[DownloadsPage] downloads_cancel error:', err);
      }
    } else {
      setDownloads((prev) =>
        prev.map((d) => (d.id === id ? { ...d, state: 'cancelled' } : d))
      );
    }
  };

  const handleClearCompleted = async () => {
    if (isTauri) {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        await invoke('downloads_clear_completed');
        fetchDownloads();
      } catch (err) {
        console.warn('[DownloadsPage] downloads_clear_completed error:', err);
      }
    } else {
      setDownloads((prev) => prev.filter((d) => d.state !== 'completed'));
    }
  };

  const formatBytes = (bytes: number): string => {
    if (bytes <= 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`;
  };

  const getFileIcon = (fileName: string) => {
    const ext = fileName.split('.').pop()?.toLowerCase();
    if (['zip', 'tar', 'gz', 'dmg', 'pkg', 'exe', '7z'].includes(ext || '')) {
      return <FileArchive className="w-5 h-5 text-[#d4ad66]" />;
    }
    if (['ts', 'js', 'json', 'rs', 'py', 'html', 'css'].includes(ext || '')) {
      return <FileCode className="w-5 h-5 text-emerald-400" />;
    }
    if (['txt', 'md', 'pdf', 'doc', 'docx'].includes(ext || '')) {
      return <FileText className="w-5 h-5 text-blue-400" />;
    }
    return <File className="w-5 h-5 text-white/60" />;
  };

  const filteredDownloads = downloads.filter((d) =>
    d.fileName.toLowerCase().includes(searchQuery.toLowerCase()) ||
    d.url.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl space-y-6 pb-12">
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-white/10">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <Download className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight text-white/95">Downloads</h1>
              <p className="text-[13px] text-white/40">
                View file transfers, open files, and locate downloads
              </p>
            </div>
          </div>
          <div className="flex items-center space-x-2">
            <button
              onClick={fetchDownloads}
              disabled={isLoading}
              title="Refresh"
              className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-white/70 hover:text-white border border-white/10 transition-colors"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>
            {downloads.some((d) => d.state === 'completed') && (
              <button
                onClick={handleClearCompleted}
                className="px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-white/70 hover:text-white text-[12px] font-medium border border-white/10 transition-colors flex items-center space-x-1.5"
              >
                <Trash2 className="w-3.5 h-3.5 text-white/50" />
                <span>Clear Completed</span>
              </button>
            )}
          </div>
        </div>

        {/* Search Bar */}
        <div className="relative">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-white/30" />
          <input
            type="text"
            placeholder="Search downloads by name or URL..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white/[0.03] border border-white/10 focus:border-[#d4ad66]/50 focus:bg-white/[0.06] text-sm text-white placeholder-white/20 outline-none transition-all"
          />
        </div>

        {/* Download Items */}
        {filteredDownloads.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-white/40 space-y-3">
            <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/5 text-white/20">
              <Download className="w-8 h-8" />
            </div>
            <p className="text-[14px] font-medium text-white/60">No downloads found</p>
            <p className="text-[12px] text-white/30 max-w-sm text-center">
              Files you download while browsing in Blanc will appear here. Private window transfers are kept ephemeral and never persisted.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredDownloads.map((item) => {
              const percent = item.totalBytes && item.totalBytes > 0
                ? Math.min(100, Math.round((item.receivedBytes / item.totalBytes) * 100))
                : 0;

              return (
                <div
                  key={item.id}
                  className="rounded-2xl border border-white/10 bg-white/[0.02] p-4 backdrop-blur-md hover:border-white/20 transition-all space-y-3"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex items-start space-x-3 min-w-0">
                      <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 shrink-0">
                        {getFileIcon(item.fileName)}
                      </div>
                      <div className="min-w-0">
                        <h3 className="text-[14px] font-medium text-white/90 truncate max-w-md">
                          {item.fileName}
                        </h3>
                        <p className="text-[11px] text-white/40 truncate max-w-md font-mono mt-0.5">
                          {item.url}
                        </p>
                        <div className="flex items-center space-x-2 text-[11px] text-white/40 mt-1">
                          <span>{formatBytes(item.receivedBytes)}</span>
                          {item.totalBytes ? (
                            <>
                              <span>/</span>
                              <span>{formatBytes(item.totalBytes)}</span>
                              <span>•</span>
                              <span>{percent}%</span>
                            </>
                          ) : null}
                          {item.isPrivate && (
                            <>
                              <span>•</span>
                              <span className="text-[#d4ad66] font-medium">Private</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Status Badge & Actions */}
                    <div className="flex items-center space-x-2 shrink-0">
                      {item.state === 'completed' && (
                        <div className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-[11px] font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Completed</span>
                        </div>
                      )}
                      {item.state === 'inProgress' && (
                        <div className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-[#d4ad66]/10 border border-[#d4ad66]/20 text-[#d4ad66] text-[11px] font-medium">
                          <Clock className="w-3.5 h-3.5 animate-spin" />
                          <span>Downloading</span>
                        </div>
                      )}
                      {item.state === 'cancelled' && (
                        <div className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-white/5 border border-white/10 text-white/40 text-[11px]">
                          <XCircle className="w-3.5 h-3.5" />
                          <span>Cancelled</span>
                        </div>
                      )}
                      {item.state === 'failed' && (
                        <div className="flex items-center space-x-1 px-2.5 py-1 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 text-[11px] font-medium">
                          <AlertCircle className="w-3.5 h-3.5" />
                          <span>Failed</span>
                        </div>
                      )}

                      {/* Action buttons */}
                      {item.state === 'completed' && (
                        <>
                          <button
                            onClick={() => handleOpen(item.id)}
                            title="Open File"
                            className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-white/70 hover:text-white border border-white/10 transition-colors"
                          >
                            <Play className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleShowInFolder(item.id)}
                            title="Show in Folder"
                            className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-white/70 hover:text-white border border-white/10 transition-colors"
                          >
                            <FolderOpen className="w-3.5 h-3.5" />
                          </button>
                        </>
                      )}
                      {item.state === 'inProgress' && (
                        <button
                          onClick={() => handleCancel(item.id)}
                          title="Cancel Download"
                          className="p-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 transition-colors"
                        >
                          <XCircle className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Progress bar for inProgress */}
                  {item.state === 'inProgress' && (
                    <div className="w-full bg-white/5 rounded-full h-1.5 overflow-hidden">
                      <div
                        className="bg-[#d4ad66] h-full transition-all duration-300 rounded-full"
                        style={{ width: `${percent > 0 ? percent : 50}%` }}
                      />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};
