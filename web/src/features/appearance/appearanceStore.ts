import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type AppTheme = 'LIGHT' | 'DARK' | 'SOFT'
export type PrimaryColor = 'VIOLET' | 'BLUE' | 'GREEN' | 'GRAY'
export type AppFont = 'INTER' | 'ROBOTO' | 'SYSTEM'
export type TextSize = 'SMALL' | 'NORMAL' | 'LARGE'

interface AppearanceState {
  panelOpen: boolean
  theme: AppTheme
  primaryColor: PrimaryColor
  font: AppFont
  textSize: TextSize
  openPanel: () => void
  closePanel: () => void
  update: (values: Partial<Pick<AppearanceState, 'theme' | 'primaryColor' | 'font' | 'textSize'>>) => void
  reset: () => void
}

const defaults = { theme: 'LIGHT' as AppTheme, primaryColor: 'VIOLET' as PrimaryColor, font: 'INTER' as AppFont, textSize: 'NORMAL' as TextSize }

export const useAppearanceStore = create<AppearanceState>()(persist((set) => ({
  panelOpen: false,
  ...defaults,
  openPanel: () => set({ panelOpen: true }),
  closePanel: () => set({ panelOpen: false }),
  update: (values) => set(values),
  reset: () => set(defaults),
}), {
  name: 'casegen.appearance.v1',
  partialize: ({ theme, primaryColor, font, textSize }) => ({ theme, primaryColor, font, textSize }),
}))
