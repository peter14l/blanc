import re

with open('src/hooks/useBrowserIPC.ts', 'r') as f:
    content = f.read()

marker = """  }, [isTauriAvailable, addHistoryEntry]);
  }

  // Poll or sync initial state
  useEffect(() => {
    if (isTauriAvailable) {
      getTabs().catch(() => {});
    }
  }, [isTauriAvailable, getTabs]);

  const activeTab = useMemo("""

new_code = """  }, [isTauriAvailable, addHistoryEntry]);
  }

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

  // Poll or sync initial state
  useEffect(() => {
    if (isTauriAvailable) {
      getTabs().catch(() => {});
    }
  }, [isTauriAvailable, getTabs]);

  const activeTab = useMemo("""

if '  }, [isTauriAvailable, addHistoryEntry];' in content and 'const activeTab = useMemo(' in content:
    new_content = content.replace(
        '  }, [isTauriAvailable, addHistoryEntry];\n  }\n\n  // Poll or sync initial state\n  useEffect(() => {\n    if (isTauriAvailable) {\n      getTabs().catch(() => {});\n    }\n  }, [isTauriAvailable, getTabs]);\n\n  const activeTab = useMemo("',
        '  }, [isTauriAvailable, addHistoryEntry];\n  }\n\n  // Group management\n  const createGroup = useCallback(\n    async (name: string): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'create_group\', { window: \'main\', name });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri create_group failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  const renameGroup = useCallback(\n    async (groupId: string, name: string): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'rename_group\', { group_id: groupId, name });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri rename_group failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  const setGroupCollapsed = useCallback(\n    async (groupId: string, collapsed: boolean): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'set_group_collapsed\', { group_id: groupId, collapsed });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri set_group_collapsed failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  const moveTabToGroup = useCallback(\n    async (tabId: string, groupId: string | null): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'move_tab_to_group\', { tab_id: tabId, group: groupId });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri move_tab_to_group failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  const closeGroup = useCallback(\n    async (groupId: string): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'close_group\', { group_id: groupId });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri close_group failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  // Closed tab recovery\n  const forgetClosedTab = useCallback(\n    async (entryId: string): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'forget_closed_tab\', { entry_id: entryId });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri forget_closed_tab failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  const clearClosedTabs = useCallback(\n    async (): Promise<void> => {\n      if (isTauriAvailable) {\n        try {\n          const { invoke } = await import(\'@tauri-apps/api/core\');\n          await invoke(\'clear_closed_tabs\', { window: \'main\' });\n        } catch (err) {\n          console.warn(\'[useBrowserIPC] Tauri clear_closed_tabs failed:\', err);\n        }\n      }\n    },\n    [isTauriAvailable]\n  );\n\n  // Poll or sync initial state\n  useEffect(() => {\n    if (isTauriAvailable) {\n      getTabs().catch(() => {});\n    }\n  }, [isTauriAvailable, getTabs]);\n\n  const activeTab = useMemo(",
        content
    )

    with open('src/hooks/useBrowserIPC.ts', 'w') as f:
        f.write(content)
    print("Successfully inserted group functions")
else:
    print("Marker not found!")

with open('src/hooks/useBrowserIPC.ts', 'r') as f:
    content = f.read()
    idx = content.find('const activeTab = useMemo(')
    if idx >= 0:
        print(f"Found activeTab at position {idx}")
    else:
        print("activeTab not found")