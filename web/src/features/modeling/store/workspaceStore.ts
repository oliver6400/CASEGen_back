import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type PanelMode = 'VISIBLE' | 'COLLAPSED' | 'HIDDEN'
interface WorkspaceSnapshot { leftMode: PanelMode; rightMode: PanelMode; toolboxVisible: boolean }
interface WorkspaceState extends WorkspaceSnapshot {
  leftWidth: number
  rightWidth: number
  restoreSnapshot: WorkspaceSnapshot | null
  setLeftMode: (mode: PanelMode) => void
  setRightMode: (mode: PanelMode) => void
  setLeftWidth: (width: number) => void
  setRightWidth: (width: number) => void
  setToolboxVisible: (visible: boolean) => void
  focusDiagram: () => void
  restorePanels: () => void
}

const defaults: WorkspaceSnapshot = { leftMode: 'VISIBLE', rightMode: 'VISIBLE', toolboxVisible: false }

export const useWorkspaceStore = create<WorkspaceState>()(persist((set) => ({
  ...defaults,
  leftWidth: 230,
  rightWidth: 280,
  restoreSnapshot: null,
  setLeftMode: (leftMode) => set({ leftMode }),
  setRightMode: (rightMode) => set({ rightMode }),
  setLeftWidth: (leftWidth) => set({ leftWidth: clamp(leftWidth) }),
  setRightWidth: (rightWidth) => set({ rightWidth: clamp(rightWidth) }),
  setToolboxVisible: (toolboxVisible) => set({ toolboxVisible }),
  focusDiagram: () => set((state) => ({
    restoreSnapshot: { leftMode: state.leftMode, rightMode: state.rightMode, toolboxVisible: state.toolboxVisible },
    leftMode: 'HIDDEN', rightMode: 'HIDDEN', toolboxVisible: false,
  })),
  restorePanels: () => set((state) => ({ ...(state.restoreSnapshot ?? defaults), restoreSnapshot: null })),
}), {
  name: 'casegen.workspace.v1',
  partialize: ({ leftMode, rightMode, leftWidth, rightWidth, toolboxVisible }) => ({ leftMode, rightMode, leftWidth, rightWidth, toolboxVisible }),
}))

function clamp(width: number) { return Math.min(420, Math.max(180, Math.round(width))) }
