import React, { useState, useMemo } from 'react';
import {
  Bookmark as BookmarkIcon,
  Search,
  Plus,
  Folder,
  Tag,
  Grid,
  List,
  Trash2,
  Edit2,
  ExternalLink,
  Globe,
  X,
} from 'lucide-react';
import { Bookmark } from '../../types/browser';

interface BookmarksPageProps {
  bookmarks: Bookmark[];
  onNavigate: (url: string) => void;
  onOpenNewTab: (url: string) => void;
  onAddBookmark: (bookmark: Omit<Bookmark, 'id' | 'createdAt'>) => void;
  onRemoveBookmark: (id: string) => void;
  onUpdateBookmark: (id: string, updates: Partial<Bookmark>) => void;
}

export const BookmarksPage: React.FC<BookmarksPageProps> = ({
  bookmarks,
  onNavigate,
  onOpenNewTab,
  onAddBookmark,
  onRemoveBookmark,
  onUpdateBookmark,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedFolder, setSelectedFolder] = useState<string>('All');
  const [selectedTag, setSelectedTag] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  // Add / Edit Modal state
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingBookmark, setEditingBookmark] = useState<Bookmark | null>(null);
  const [formTitle, setFormTitle] = useState('');
  const [formUrl, setFormUrl] = useState('');
  const [formFolder, setFormFolder] = useState('Favorites');
  const [formTags, setFormTags] = useState('');

  // Extract distinct folders and tags
  const { folders, allTags } = useMemo(() => {
    const fSet = new Set<string>(['Favorites', 'Reading List', 'Work']);
    const tSet = new Set<string>();

    bookmarks.forEach((b) => {
      if (b.folder) fSet.add(b.folder);
      if (b.tags) b.tags.forEach((t) => tSet.add(t));
    });

    return {
      folders: Array.from(fSet),
      allTags: Array.from(tSet),
    };
  }, [bookmarks]);

  // Filter bookmarks
  const filteredBookmarks = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return bookmarks.filter((b) => {
      const matchesSearch =
        !q ||
        b.title.toLowerCase().includes(q) ||
        b.url.toLowerCase().includes(q) ||
        b.tags?.some((t) => t.toLowerCase().includes(q));

      const matchesFolder = selectedFolder === 'All' || b.folder === selectedFolder;
      const matchesTag = !selectedTag || b.tags?.includes(selectedTag);

      return matchesSearch && matchesFolder && matchesTag;
    });
  }, [bookmarks, searchQuery, selectedFolder, selectedTag]);

  const openAddModal = () => {
    setEditingBookmark(null);
    setFormTitle('');
    setFormUrl('https://');
    setFormFolder(selectedFolder === 'All' ? 'Favorites' : selectedFolder);
    setFormTags('');
    setIsModalOpen(true);
  };

  const openEditModal = (b: Bookmark) => {
    setEditingBookmark(b);
    setFormTitle(b.title);
    setFormUrl(b.url);
    setFormFolder(b.folder || 'Favorites');
    setFormTags(b.tags ? b.tags.join(', ') : '');
    setIsModalOpen(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formTitle.trim() || !formUrl.trim()) return;

    let cleanUrl = formUrl.trim();
    if (
      !cleanUrl.startsWith('http://') &&
      !cleanUrl.startsWith('https://') &&
      !cleanUrl.startsWith('blanc://')
    ) {
      cleanUrl = `https://${cleanUrl}`;
    }

    const tagsArray = formTags
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean);

    if (editingBookmark) {
      onUpdateBookmark(editingBookmark.id, {
        title: formTitle.trim(),
        url: cleanUrl,
        folder: formFolder.trim() || 'Favorites',
        tags: tagsArray,
      });
    } else {
      onAddBookmark({
        title: formTitle.trim(),
        url: cleanUrl,
        folder: formFolder.trim() || 'Favorites',
        tags: tagsArray,
      });
    }

    setIsModalOpen(false);
  };

  return (
    <div className="w-full h-full overflow-y-auto bg-transparent text-white px-6 py-8 flex flex-col items-center select-none font-ui">
      <div className="w-full max-w-4xl space-y-6">
        {/* Header Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between pb-4 border-b border-white/10 gap-3">
          <div className="flex items-center space-x-3">
            <div className="p-2 rounded-xl bg-white/5 border border-white/10 text-[#d4ad66]">
              <BookmarkIcon className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold text-white/95">Bookmarks</h1>
              <p className="text-[12px] text-white/40">
                {bookmarks.length} saved {bookmarks.length === 1 ? 'bookmark' : 'bookmarks'}
              </p>
            </div>
          </div>

          <div className="flex items-center space-x-2 self-end sm:self-auto">
            {/* Grid / List View Toggle */}
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

            {/* Add Bookmark Button */}
            <button
              onClick={openAddModal}
              className="flex items-center space-x-1.5 px-3.5 py-1.5 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] text-[#12100b] text-[12px] font-semibold transition-colors"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add Bookmark</span>
            </button>
          </div>
        </div>

        {/* Filter Controls: Search & Folder Tabs */}
        <div className="space-y-3">
          {/* Search bar */}
          <div className="relative">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-white/30" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search bookmarks by title, address, or tag..."
              className="w-full pl-10 pr-10 py-2 rounded-xl bg-white/[0.04] hover:bg-white/[0.07] focus:bg-black/40 border border-white/10 focus:border-[#d4ad66]/50 text-white text-[13px] outline-none transition-all placeholder-white/30"
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

          {/* Folder Pills */}
          <div className="flex items-center space-x-2 overflow-x-auto pb-1 text-[12px]">
            <button
              onClick={() => {
                setSelectedFolder('All');
                setSelectedTag(null);
              }}
              className={`px-3 py-1 rounded-xl border transition-colors shrink-0 ${
                selectedFolder === 'All' && !selectedTag
                  ? 'bg-white/15 border-white/20 text-white font-medium'
                  : 'bg-white/[0.02] border-white/5 text-white/60 hover:bg-white/[0.06] hover:text-white'
              }`}
            >
              All Folders ({bookmarks.length})
            </button>

            {folders.map((folder) => {
              const count = bookmarks.filter((b) => b.folder === folder).length;
              const isSelected = selectedFolder === folder && !selectedTag;
              return (
                <button
                  key={folder}
                  onClick={() => {
                    setSelectedFolder(folder);
                    setSelectedTag(null);
                  }}
                  className={`flex items-center space-x-1.5 px-3 py-1 rounded-xl border transition-colors shrink-0 ${
                    isSelected
                      ? 'bg-white/15 border-white/20 text-white font-medium'
                      : 'bg-white/[0.02] border-white/5 text-white/60 hover:bg-white/[0.06] hover:text-white'
                  }`}
                >
                  <Folder className="w-3 h-3 text-[#d4ad66]" />
                  <span>{folder}</span>
                  <span className="text-[10px] text-white/30">({count})</span>
                </button>
              );
            })}
          </div>

          {/* Tags Pills (if any tags exist) */}
          {allTags.length > 0 && (
            <div className="flex items-center space-x-1.5 overflow-x-auto text-[11px] pt-1">
              <span className="text-white/30 flex items-center space-x-1 mr-1">
                <Tag className="w-3 h-3" />
                <span>Tags:</span>
              </span>
              {allTags.map((t) => {
                const isSelected = selectedTag === t;
                return (
                  <button
                    key={t}
                    onClick={() => setSelectedTag(isSelected ? null : t)}
                    className={`px-2 py-0.5 rounded-lg border transition-colors ${
                      isSelected
                        ? 'bg-[#d4ad66]/20 border-[#d4ad66]/40 text-[#d4ad66]'
                        : 'bg-white/5 border-white/5 text-white/50 hover:text-white'
                    }`}
                  >
                    #{t}
                  </button>
                );
              })}
              {selectedTag && (
                <button
                  onClick={() => setSelectedTag(null)}
                  className="text-white/40 hover:text-white text-[10px] underline ml-1"
                >
                  Clear tag
                </button>
              )}
            </div>
          )}
        </div>

        {/* Content Display: Grid or List */}
        {filteredBookmarks.length === 0 ? (
          <div className="py-16 text-center rounded-2xl bg-white/[0.02] border border-white/5 space-y-3">
            <div className="w-12 h-12 rounded-2xl bg-white/5 flex items-center justify-center mx-auto text-white/30">
              <BookmarkIcon className="w-6 h-6" />
            </div>
            <div className="space-y-1">
              <p className="text-[14px] font-medium text-white/80">No bookmarks found</p>
              <p className="text-[12px] text-white/40">
                {searchQuery || selectedTag
                  ? 'Try changing your search query or tag filter'
                  : 'Click "Add Bookmark" to save your favorite websites'}
              </p>
            </div>
          </div>
        ) : viewMode === 'grid' ? (
          /* GRID VIEW */
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {filteredBookmarks.map((b) => {
              const domain = b.url.replace(/^https?:\/\//, '').split('/')[0];
              return (
                <div
                  key={b.id}
                  onClick={() => onNavigate(b.url)}
                  className="group relative flex flex-col justify-between p-3.5 rounded-2xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/5 hover:border-white/15 transition-all cursor-pointer"
                >
                  <div>
                    {/* Top Row: Icon, Title, Actions */}
                    <div className="flex items-start justify-between space-x-2 mb-2">
                      <div className="flex items-center space-x-2.5 min-w-0">
                        <div className="w-8 h-8 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center text-[#d4ad66] shrink-0">
                          {b.favicon ? (
                            <img src={b.favicon} alt="" className="w-4 h-4 rounded" />
                          ) : (
                            <Globe className="w-4 h-4" />
                          )}
                        </div>
                        <div className="min-w-0">
                          <p className="text-[13px] font-medium text-white/90 truncate group-hover:text-[#d4ad66] transition-colors">
                            {b.title}
                          </p>
                          <p className="text-[11px] text-white/40 truncate">{domain}</p>
                        </div>
                      </div>

                      {/* Action buttons on hover */}
                      <div
                        className="flex items-center space-x-0.5 opacity-0 group-hover:opacity-100 transition-opacity"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <button
                          onClick={() => onOpenNewTab(b.url)}
                          title="Open in new tab"
                          className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => openEditModal(b)}
                          title="Edit"
                          className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => onRemoveBookmark(b.id)}
                          title="Delete"
                          className="p-1 rounded-md text-white/40 hover:text-red-400 hover:bg-red-500/10"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Footer: Folder & Tags */}
                  <div className="flex items-center justify-between pt-2 border-t border-white/5 text-[11px] text-white/40">
                    <span className="flex items-center space-x-1 truncate">
                      <Folder className="w-3 h-3 text-[#d4ad66]" />
                      <span>{b.folder || 'Favorites'}</span>
                    </span>

                    {b.tags && b.tags.length > 0 && (
                      <div className="flex items-center space-x-1">
                        {b.tags.slice(0, 2).map((tag) => (
                          <span
                            key={tag}
                            className="px-1.5 py-0.2 rounded bg-white/5 text-white/50 text-[10px]"
                          >
                            #{tag}
                          </span>
                        ))}
                        {b.tags.length > 2 && (
                          <span className="text-[10px] text-white/30">
                            +{b.tags.length - 2}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* LIST VIEW */
          <div className="rounded-2xl border border-white/5 bg-white/[0.02] overflow-hidden divide-y divide-white/5">
            {filteredBookmarks.map((b) => {
              const domain = b.url.replace(/^https?:\/\//, '').split('/')[0];
              return (
                <div
                  key={b.id}
                  onClick={() => onNavigate(b.url)}
                  className="group flex items-center justify-between px-4 py-3 hover:bg-white/[0.04] transition-colors cursor-pointer"
                >
                  <div className="flex items-center space-x-3 min-w-0 flex-1">
                    <div className="w-7 h-7 rounded-lg bg-white/5 flex items-center justify-center text-[#d4ad66] shrink-0">
                      {b.favicon ? (
                        <img src={b.favicon} alt="" className="w-4 h-4 rounded" />
                      ) : (
                        <Globe className="w-3.5 h-3.5" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1 pr-4">
                      <p className="text-[13px] font-medium text-white/90 truncate group-hover:text-[#d4ad66] transition-colors">
                        {b.title}
                      </p>
                      <div className="flex items-center space-x-2 text-[11px] text-white/40">
                        <span className="truncate max-w-[240px]">{domain}</span>
                        <span>•</span>
                        <span className="text-[#d4ad66]">{b.folder || 'Favorites'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2 shrink-0">
                    {b.tags?.map((t) => (
                      <span
                        key={t}
                        className="hidden md:inline px-1.5 py-0.5 rounded bg-white/5 text-[10px] text-white/40"
                      >
                        #{t}
                      </span>
                    ))}

                    <div
                      className="flex items-center space-x-1 opacity-0 group-hover:opacity-100 transition-opacity ml-2"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <button
                        onClick={() => onOpenNewTab(b.url)}
                        title="Open in new tab"
                        className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => openEditModal(b)}
                        title="Edit"
                        className="p-1.5 rounded-lg text-white/40 hover:text-white hover:bg-white/10"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => onRemoveBookmark(b.id)}
                        title="Delete"
                        className="p-1.5 rounded-lg text-white/40 hover:text-red-400 hover:bg-red-500/10"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ADD / EDIT BOOKMARK MODAL */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <div className="relative w-full max-w-md bg-[#1c1c1c] border border-white/10 rounded-2xl shadow-2xl p-5 animate-slide-down">
            <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
              <h2 className="text-[15px] font-semibold text-white/90">
                {editingBookmark ? 'Edit Bookmark' : 'Add Bookmark'}
              </h2>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1 rounded-md text-white/40 hover:text-white hover:bg-white/10"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-3.5">
              <div>
                <label className="block text-[12px] font-medium text-white/70 mb-1">
                  Title
                </label>
                <input
                  type="text"
                  required
                  value={formTitle}
                  onChange={(e) => setFormTitle(e.target.value)}
                  placeholder="Website title"
                  className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60"
                />
              </div>

              <div>
                <label className="block text-[12px] font-medium text-white/70 mb-1">
                  URL
                </label>
                <input
                  type="text"
                  required
                  value={formUrl}
                  onChange={(e) => setFormUrl(e.target.value)}
                  placeholder="https://example.com"
                  className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60 font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[12px] font-medium text-white/70 mb-1">
                    Folder
                  </label>
                  <input
                    type="text"
                    value={formFolder}
                    onChange={(e) => setFormFolder(e.target.value)}
                    placeholder="Folder name"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60"
                  />
                </div>

                <div>
                  <label className="block text-[12px] font-medium text-white/70 mb-1">
                    Tags (comma separated)
                  </label>
                  <input
                    type="text"
                    value={formTags}
                    onChange={(e) => setFormTags(e.target.value)}
                    placeholder="dev, tools, news"
                    className="w-full px-3 py-2 rounded-xl bg-white/5 border border-white/10 text-white text-[13px] outline-none focus:border-[#d4ad66]/60"
                  />
                </div>
              </div>

              <div className="flex items-center justify-end space-x-2 pt-3 border-t border-white/10">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-3.5 py-1.5 rounded-xl text-white/60 hover:text-white hover:bg-white/10 text-[12px]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 rounded-xl bg-[#d4ad66] hover:bg-[#e5be73] text-[#12100b] font-semibold text-[12px] transition-colors"
                >
                  {editingBookmark ? 'Save Changes' : 'Add Bookmark'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
