import React, { useCallback, useEffect, useState } from 'react';
import { KeyRound, Search, RefreshCw, Trash2, ExternalLink } from 'lucide-react';

interface StoredCredentialSummary {
  id: string;
  url: string;
  title: string;
  username: string;
  createdAt: number;
  lastUsed: number;
}

interface PasswordsPageProps {
  onNavigate: (url: string) => void;
}

const isTauri = (): boolean => {
  return (
    typeof window !== 'undefined' &&
    ('__TAURI_INTERNALS__' in window || '__TAURI__' in window)
  );
};

export const PasswordsPage: React.FC<PasswordsPageProps> = ({ onNavigate }) => {
  const [passwords, setPasswords] = useState<StoredCredentialSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const loadPasswords = useCallback(async () => {
    if (!isTauri()) return;
    try {
      setLoading(true);
      const { invoke } = await import('@tauri-apps/api/core');
      setPasswords(await invoke<StoredCredentialSummary[]>('list_stored_passwords'));
    } catch (e) {
      console.warn('[Passwords] list_stored_passwords failed:', e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPasswords();
  }, [loadPasswords]);

  const handleDelete = async (id: string) => {
    if (!isTauri()) return;
    try {
      const { invoke } = await import('@tauri-apps/api/core');
      await invoke('delete_stored_password', { id });
      setPasswords((prev) => prev.filter((p) => p.id !== id));
    } catch (e) {
      console.warn('[Passwords] delete_stored_password failed:', e);
    }
  };

  const q = searchQuery.trim().toLowerCase();
  const filtered = passwords.filter(
    (p) =>
      p.title.toLowerCase().includes(q) ||
      p.username.toLowerCase().includes(q) ||
      p.url.toLowerCase().includes(q)
  );

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-3xl">
        {/* Header Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between pb-4 mb-6 border-b border-white/10 gap-3">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <KeyRound className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-white/95">Saved Passwords</h1>
              <p className="text-[12px] text-white/40">
                {passwords.length} {passwords.length === 1 ? 'password' : 'passwords'} stored in Blanc's encrypted local vault
              </p>
            </div>
          </div>

          <button
            onClick={loadPasswords}
            disabled={loading}
            className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-white/70 border border-white/10 text-[12px] font-medium transition-colors self-end sm:self-auto disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>

        {/* Search Input */}
        <div className="relative mb-6">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-white/30" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search saved passwords…"
            className="w-full bg-white/[0.03] border border-white/10 rounded-xl pl-10 pr-4 py-2.5 text-[14px] text-white/90 placeholder-white/30 outline-none focus:border-[#d4ad66]/40 focus:ring-1 focus:ring-[#d4ad66]/20 transition-colors"
          />
        </div>

        {!isTauri() ? (
          <div className="p-8 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
            <KeyRound className="w-6 h-6 text-white/20 mx-auto mb-3" />
            <p className="text-[13.5px] text-white/50">
              Saved passwords are encrypted on-device and managed in the desktop app.
            </p>
          </div>
        ) : loading && passwords.length === 0 ? (
          <div className="p-8 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
            <p className="text-[13.5px] text-white/40">Loading…</p>
          </div>
        ) : passwords.length === 0 ? (
          <div className="p-8 rounded-2xl bg-white/[0.02] border border-white/5 text-center">
            <KeyRound className="w-6 h-6 text-white/20 mx-auto mb-3" />
            <p className="text-[13.5px] text-white/50">
              No saved passwords yet. Enable Saved Passwords during browser import
              (Settings → Import &amp; Data Migration), or import a passwords CSV.
            </p>
          </div>
        ) : (
          <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
            {filtered.length === 0 ? (
              <p className="px-4 py-8 text-center text-[12.5px] text-white/40">
                No saved passwords match "{searchQuery}"
              </p>
            ) : (
              filtered.map((p) => (
                <div key={p.id} className="flex items-center justify-between px-4 py-3">
                  <div className="min-w-0 pr-3">
                    <p className="text-[13.5px] font-medium text-white/90 truncate">
                      {p.title || p.url}
                    </p>
                    <p className="text-[11.5px] text-white/40 truncate">
                      {p.username || 'No username'} · <span className="font-mono">{p.url}</span>
                    </p>
                  </div>
                  <div className="flex items-center space-x-1 shrink-0">
                    <button
                      onClick={() => onNavigate(p.url.startsWith('http') ? p.url : `https://${p.url}`)}
                      title="Open site"
                      className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10 transition-colors"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => handleDelete(p.id)}
                      title="Delete password"
                      className="p-1.5 rounded-lg text-white/40 hover:text-red-400 hover:bg-red-500/10 transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}
      </div>
    </div>
  );
};