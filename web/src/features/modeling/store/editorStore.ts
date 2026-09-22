import { create } from 'zustand'

interface EditorState {
  selectedElementId: string | null
  presenceActivity: 'VIEWING' | 'SELECTING' | 'EDITING'
  selectElement: (elementId: string | null) => void
  setEditing: (editing: boolean) => void
}

export const useEditorStore = create<EditorState>((set) => ({
  selectedElementId: null,
  presenceActivity: 'VIEWING',
  selectElement: (selectedElementId) => set({
    selectedElementId,
    presenceActivity: selectedElementId ? 'SELECTING' : 'VIEWING',
  }),
  setEditing: (editing) => set((state) => ({
    presenceActivity: editing && state.selectedElementId ? 'EDITING' : state.selectedElementId ? 'SELECTING' : 'VIEWING',
  })),
}))
