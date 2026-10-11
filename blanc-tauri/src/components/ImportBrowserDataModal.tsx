import React, { useState, useEffect, useRef } from 'react';
import {
  Bookmark,
  History,
  Lock,
  Layers,
  Upload,
  FileText,
  X,
  Check,
  AlertTriangle,
  ChevronDown,
  Loader2,
} from 'lucide-react';

// ---------------------------------------------------------------------------
// Types (matching Rust serde output)
// ---------------------------------------------------------------------------

export interface BrowserProfileSource {
  id: string;
  browserId: string;
  browserName: string;
  profileName: string;
  profilePath: string;
  hasBookmarks: boolean;
  hasHistory: boolean;
  hasPasswords: boolean;
  hasSessions: boolean;
}

export interface ImportOptions {
  sourceId: string;
  importBookmarks: boolean;
  importHistory: boolean;
  importPasswords: boolean;
  importOpenTabs: boolean;
}

export interface ImportResult {
  bookmarksImported: number;
  bookmarksSkipped: number;
  historyImported: number;
  passwordsImported: number;
  tabsImported: number;
  warnings: string[];
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const BROWSER_ICONS: Record<string, string> = {
  chrome: '🌐',
  edge: '🔵',
  brave: '🦁',
  vivaldi: '🎶',
  opera: '🅾️',
  chromium: '⚙️',
  firefox: '🦊',
};

const browserIcon = (id: string) => BROWSER_ICONS[id] ?? '🌐';

type Step = 'select' | 'options' | 'running' | 'done';

interface Props {
  onClose: () => void;
  onBookmarksUpdated?: () => void;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export const ImportBrowserDataModal: React.FC<Props> = ({ onClose, onBookmarksUpdated }) => {
  const [step, setStep] = useState<Step>('select');
  const [sources, setSources] = useState<BrowserProfileSource[]>([]);
  const [loadingSources, setLoadingSources] = useState(true);
  const [selectedSource, setSelectedSource] = useState<BrowserProfileSource | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);

  const [importBookmarks, setImportBookmarks] = useState(true);
  const [importHistory, setImportHistory] = useState(true);
  const [importPasswords, setImportPasswords] = useState(false);
  const [importTabs, setImportTabs] = useState(false);

  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  // HTML / CSV file import (manual file picker)
  const htmlInputRef = useRef<HTMLInputElement>(null);
  const csvInputRef = useRef<HTMLInputElement>(null);

  const isTauri = () =>
    typeof window !== 'undefined' &&
    ('__TAURI_INTERNALS__' in window || '__TAURI__' in window);

  // Discover browser sources on mount.
  useEffect(() => {
    if (!isTauri()) {
      // Dev/web fallback: mock data.
      setSources([]);
      setLoadingSources(false);
      return;
    }
    (async () => {
      try {
        const { invoke } = await import('@tauri-apps/api/core');
        const discovered = await invoke<BrowserProfileSource[]>('discover_browser_sources');
        setSources(discovered);
        if (discovered.length > 0) setSelectedSource(discovered[0]);
      } catch (e) {
        console.warn('[ImportModal] discover_browser_sources failed:', e);
      } finally {
        setLoadingSources(false);
      }
    })();
  }, []);

  const handleStartImport = async () => {
    if (!selectedSource) return;
    setStep('running');
    setError(null);

    try {
      if (isTauri()) {
        const { invoke } = await import('@tauri-apps/api/core');
        const res = await invoke<ImportResult>('import_browser_data', {
          options: {
            sourceId: selectedSource.id,
            importBookmarks,
            importHistory,
            importPasswords,
            importOpenTabs: importTabs,
          } satisfies ImportOptions,
        });
        setResult(res);
      } else {
        // Non-Tauri mock result for dev preview.
        setResult({
          bookmarksImported: 42,
          bookmarksSkipped: 3,
          historyImported: 128,
          passwordsImported: 0,
          tabsImported: 0,
          warnings: [],
        });
      }
      setStep('done');
      onBookmarksUpdated?.();
    } catch (e: unknown) {
      setError(String(e));
      setStep('done');
    }
  };

  const handleHtmlFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const content = await file.text();
    setStep('running');
    try {
      if (isTauri()) {
        const { invoke } = await import('@tauri-apps/api/core');
        const res = await invoke<ImportResult>('import_bookmarks_html', { fileContent: content });
        setResult(res);
      } else {
        setResult({ bookmarksImported: 10, bookmarksSkipped: 0, historyImported: 0, passwordsImported: 0, tabsImported: 0, warnings: [] });
      }
      setStep('done');
      onBookmarksUpdated?.();
    } catch (err) {
      setError(String(err));
      setStep('done');
    }
  };

  const handleCsvFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const content = await file.text();
    setStep('running');
    try {
      if (isTauri()) {
        const { invoke } = await import('@tauri-apps/api/core');
        const res = await invoke<ImportResult>('import_passwords_csv', { fileContent: content });
        setResult(res);
      } else {
        setResult({ bookmarksImported: 0, bookmarksSkipped: 0, historyImported: 0, passwordsImported: 5, tabsImported: 0, warnings: [] });
      }
      setStep('done');
    } catch (err) {
      setError(String(err));
      setStep('done');
    }
  };

  // ---------------------------------------------------------------------------
  // Render helpers
  // ---------------------------------------------------------------------------

  const Toggle = ({
    checked,
    onChange,
    disabled,
  }: {
    checked: boolean;
    onChange: (v: boolean) => void;
    disabled?: boolean;
  }) => (
    <label className="relative inline-flex items-center cursor-pointer shrink-0">
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="sr-only peer"
      />
      <div
        className={`w-10 h-5 rounded-full transition-all
          ${disabled ? 'opacity-30 cursor-not-allowed' : ''}
          bg-white/10 peer-focus:outline-none
          peer peer-checked:after:translate-x-full peer-checked:after:border-white
          after:content-[''] after:absolute after:top-[2px] after:left-[2px]
          after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all
          peer-checked:bg-[#d4ad66]`}
      />
    </label>
  );

  const CheckRow = ({
    icon,
    label,
    sublabel,
    checked,
    onChange,
    disabled,
    notice,
  }: {
    icon: React.ReactNode;
    label: string;
    sublabel?: string;
    checked: boolean;
    onChange: (v: boolean) => void;
    disabled?: boolean;
    notice?: string;
  }) => (
    <div
      className={`flex items-center justify-between p-3.5 rounded-2xl border transition-all
        ${checked && !disabled ? 'bg-white/[0.06] border-white/15' : 'bg-white/[0.02] border-white/5'}
        ${disabled ? 'opacity-40' : ''}`}
    >
      <div className="flex items-center space-x-3 min-w-0">
        <div className="text-[#d4ad66] shrink-0">{icon}</div>
        <div className="min-w-0">
          <p className="text-[13px] font-medium text-white/90">{label}</p>
          {sublabel && <p className="text-[11px] text-white/40 leading-snug">{sublabel}</p>}
          {notice && (
            <p className="text-[11px] text-amber-400/80 leading-snug mt-0.5 flex items-center gap-1">
              <AlertTriangle className="w-3 h-3 shrink-0" />
              {notice}
            </p>
          )}
        </div>
      </div>
      <Toggle checked={checked} onChange={onChange} disabled={disabled} />
    </div>
  );

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="relative w-full max-w-lg mx-4 bg-[#111] border border-white/10 rounded-3xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-white/10">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <Upload className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-[15px] font-semibold text-white/95">Import Browser Data</h2>
              <p className="text-[11px] text-white/40">
                Bring your bookmarks, history, and passwords into Blanc
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl text-white/40 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="px-6 py-5 space-y-5 max-h-[70vh] overflow-y-auto">

          {/* STEP: Select source */}
          {(step === 'select' || step === 'options') && (
            <>
              {/* Manual file import shortcuts */}
              <div className="grid grid-cols-2 gap-2">
                <button
                  onClick={() => htmlInputRef.current?.click()}
                  className="flex flex-col items-center gap-1.5 p-3 rounded-2xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/5 hover:border-white/15 transition-all text-white/70 hover:text-white"
                >
                  <FileText className="w-4 h-4 text-[#d4ad66]" />
                  <span className="text-[11px] font-medium">Import Bookmarks HTML</span>
                  <span className="text-[10px] text-white/30">Netscape format from any browser</span>
                </button>
                <button
                  onClick={() => csvInputRef.current?.click()}
                  className="flex flex-col items-center gap-1.5 p-3 rounded-2xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/5 hover:border-white/15 transition-all text-white/70 hover:text-white"
                >
                  <Lock className="w-4 h-4 text-[#d4ad66]" />
                  <span className="text-[11px] font-medium">Import Passwords CSV</span>
                  <span className="text-[10px] text-white/30">From Chrome, Firefox, Edge</span>
                </button>
              </div>

              <div className="flex items-center gap-3 text-[11px] text-white/25">
                <div className="flex-1 h-px bg-white/10" />
                <span>or detect installed browsers</span>
                <div className="flex-1 h-px bg-white/10" />
              </div>

              {/* Browser source selector */}
              <div className="space-y-2">
                <p className="text-[12px] font-medium text-white/70">Select browser profile</p>

                {loadingSources ? (
                  <div className="flex items-center gap-2 text-white/40 text-[12px] py-3">
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Searching for installed browsers...</span>
                  </div>
                ) : sources.length === 0 ? (
                  <div className="p-3.5 rounded-2xl bg-white/[0.02] border border-white/5 text-[12px] text-white/40">
                    No installed browser profiles found on this machine.
                    Use the HTML or CSV import above.
                  </div>
                ) : (
                  <div className="relative">
                    <button
                      onClick={() => setShowDropdown(!showDropdown)}
                      className="w-full flex items-center justify-between px-4 py-2.5 rounded-2xl bg-white/[0.04] hover:bg-white/[0.07] border border-white/10 text-[13px] text-white/90 transition-all"
                    >
                      <span className="flex items-center gap-2.5">
                        <span>{selectedSource ? browserIcon(selectedSource.browserId) : '🌐'}</span>
                        <span>
                          {selectedSource
                            ? `${selectedSource.browserName} — ${selectedSource.profileName}`
                            : 'Select a browser...'}
                        </span>
                      </span>
                      <ChevronDown className="w-4 h-4 text-white/40 shrink-0" />
                    </button>

                    {showDropdown && (
                      <div className="absolute top-full left-0 right-0 mt-1 z-10 bg-[#1a1a1a] border border-white/15 rounded-2xl shadow-xl overflow-hidden">
                        {sources.map((s) => (
                          <button
                            key={s.id}
                            onClick={() => {
                              setSelectedSource(s);
                              setShowDropdown(false);
                              setStep('options');
                            }}
                            className={`w-full flex items-center gap-2.5 px-4 py-2.5 text-[12px] text-left hover:bg-white/10 transition-colors
                              ${selectedSource?.id === s.id ? 'bg-white/[0.06] text-white' : 'text-white/80'}`}
                          >
                            <span>{browserIcon(s.browserId)}</span>
                            <span className="font-medium">{s.browserName}</span>
                            <span className="text-white/40">— {s.profileName}</span>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Options */}
              {selectedSource && step === 'options' && (
                <div className="space-y-2">
                  <p className="text-[12px] font-medium text-white/70 pt-1">What to import</p>
                  <div className="space-y-2">
                    <CheckRow
                      icon={<Bookmark className="w-4 h-4" />}
                      label="Bookmarks & Folders"
                      sublabel="Import all bookmarks preserving folder structure"
                      checked={importBookmarks}
                      onChange={setImportBookmarks}
                      disabled={!selectedSource.hasBookmarks}
                    />
                    <CheckRow
                      icon={<History className="w-4 h-4" />}
                      label="Browsing History"
                      sublabel="Up to 5,000 most recent visits"
                      checked={importHistory}
                      onChange={setImportHistory}
                      disabled={!selectedSource.hasHistory}
                    />
                    <CheckRow
                      icon={<Layers className="w-4 h-4" />}
                      label="Open Tabs"
                      sublabel="Bring your current session across"
                      checked={importTabs}
                      onChange={setImportTabs}
                      disabled={!selectedSource.hasSessions}
                    />
                    <CheckRow
                      icon={<Lock className="w-4 h-4" />}
                      label="Saved Passwords"
                      sublabel="Stored in Blanc's encrypted local vault"
                      checked={importPasswords}
                      onChange={setImportPasswords}
                      disabled={!selectedSource.hasPasswords}
                      notice={
                        selectedSource.browserId === 'chrome'
                          ? 'Chrome 127+ uses App-Bound Encryption. Use CSV export if direct import fails.'
                          : undefined
                      }
                    />
                  </div>
                </div>
              )}
            </>
          )}

          {/* STEP: Running */}
          {step === 'running' && (
            <div className="flex flex-col items-center gap-4 py-8">
              <Loader2 className="w-8 h-8 text-[#d4ad66] animate-spin" />
              <div className="text-center space-y-1">
                <p className="text-[14px] font-medium text-white/90">Importing...</p>
                <p className="text-[12px] text-white/40">
                  Reading browser data — this may take a few seconds.
                </p>
              </div>
            </div>
          )}

          {/* STEP: Done */}
          {step === 'done' && (
            <div className="space-y-4">
              {error ? (
                <div className="p-4 rounded-2xl bg-red-500/10 border border-red-500/20 text-[12px] text-red-400">
                  <p className="font-medium mb-1">Import failed</p>
                  <p className="text-red-400/70">{error}</p>
                </div>
              ) : result ? (
                <>
                  <div className="p-4 rounded-2xl bg-white/[0.03] border border-white/10 space-y-3">
                    <div className="flex items-center gap-2 text-[#d4ad66]">
                      <Check className="w-4 h-4" />
                      <span className="text-[13px] font-semibold text-white/90">Import complete</span>
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-[12px]">
                      {result.bookmarksImported > 0 && (
                        <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/5">
                          <p className="text-[#d4ad66] font-semibold text-[16px]">
                            {result.bookmarksImported}
                          </p>
                          <p className="text-white/50">bookmarks imported</p>
                          {result.bookmarksSkipped > 0 && (
                            <p className="text-white/30 text-[10px]">
                              {result.bookmarksSkipped} duplicates skipped
                            </p>
                          )}
                        </div>
                      )}
                      {result.historyImported > 0 && (
                        <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/5">
                          <p className="text-[#d4ad66] font-semibold text-[16px]">
                            {result.historyImported}
                          </p>
                          <p className="text-white/50">history visits</p>
                        </div>
                      )}
                      {result.passwordsImported > 0 && (
                        <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/5">
                          <p className="text-[#d4ad66] font-semibold text-[16px]">
                            {result.passwordsImported}
                          </p>
                          <p className="text-white/50">passwords saved</p>
                        </div>
                      )}
                      {result.tabsImported > 0 && (
                        <div className="p-2.5 rounded-xl bg-white/[0.04] border border-white/5">
                          <p className="text-[#d4ad66] font-semibold text-[16px]">
                            {result.tabsImported}
                          </p>
                          <p className="text-white/50">tabs restored</p>
                        </div>
                      )}
                    </div>
                  </div>

                  {result.warnings.length > 0 && (
                    <div className="space-y-1.5">
                      {result.warnings.map((w, i) => (
                        <div
                          key={i}
                          className="flex gap-2 p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-[11px] text-amber-400/80"
                        >
                          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                          <span>{w}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              ) : null}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 pb-5 pt-3 border-t border-white/10 flex items-center justify-between gap-3">
          {step === 'done' ? (
            <button
              onClick={onClose}
              className="ml-auto px-5 py-2 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] text-[#12100b] text-[12px] font-semibold transition-colors"
            >
              Done
            </button>
          ) : (
            <>
              <button
                onClick={onClose}
                className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-white/70 text-[12px] transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={
                  selectedSource && step === 'options'
                    ? handleStartImport
                    : () => setStep('options')
                }
                disabled={loadingSources || step === 'running' || sources.length === 0}
                className="px-5 py-2 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] disabled:opacity-40 disabled:cursor-not-allowed text-[#12100b] text-[12px] font-semibold transition-colors"
              >
                {step === 'options' ? 'Import' : 'Continue'}
              </button>
            </>
          )}
        </div>

        {/* Hidden file inputs */}
        <input ref={htmlInputRef} type="file" accept=".html,.htm" className="hidden" onChange={handleHtmlFile} />
        <input ref={csvInputRef} type="file" accept=".csv" className="hidden" onChange={handleCsvFile} />
      </div>
    </div>
  );
};

export default ImportBrowserDataModal;
