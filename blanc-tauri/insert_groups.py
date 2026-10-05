with open('src/hooks/useBrowserIPC.ts', 'r') as f:
    content = f.read()

marker = """  }, [isTauriAvailable, tabs]);

  // Reopen closed tab
  const reopenClosedTab = useCallback("""

new_code = """  }, [isTauriAvailable, tabs];

  // Group management
  const createGroup = useCallback(
    async (name: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('create_group', { window: 'main', name });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri create_group failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  const renameGroup = useCallback(
    async (groupId: string, name: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('rename_group', { group_id: groupId, name });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri rename_group failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  const setGroupCollapsed = useCallback(
    async (groupId: string, collapsed: boolean): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('set_group_collapsed', { group_id: groupId, collapsed });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri set_group_collapsed failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  const moveTabToGroup = useCallback(
    async (tabId: string, groupId: string | null): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('move_tab_to_group', { tab_id: tabId, group: groupId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri move_tab_to_group failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  const closeGroup = useCallback(
    async (groupId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('close_group', { group_id: groupId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri close_group failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  // Closed tab recovery
  const forgetClosedTab = useCallback(
    async (entryId: string): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('forget_closed_tab', { entry_id: entryId });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri forget_closed_tab failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  const clearClosedTabs = useCallback(
    async (): Promise<void> => {
      if (isTauriAvailable) {
        try {
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('clear_closed_tabs', { window: 'main' });
        } catch (err) {
          console.warn('[useBrowserIPC] Tauri clear_closed_tabs failed:', err);
        }
      }
    },
    [isTauriAvailable]
  );

  // Reopen closed tab
  const reopenClosedTab = useCallback("""

if marker in content:
    new_content = content.replace(marker, new_code)
    with open('src/hooks/useBrowserIPC.ts', 'w') as f:
        f.write(content)
    print("Successfully inserted group functions")
else:
    print("Marker not found!")
    idx = content.find("}, [isTauriAvailable, tabs]);")
    if idx >= 0:
        print(f"Found at position {idx}")
        print(repr(content[idx:idx+50]))
    else:
        print("Marker not found at all")