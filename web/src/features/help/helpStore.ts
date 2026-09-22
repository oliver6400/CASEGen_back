import { create } from 'zustand'

interface HelpState {
  tourOpen: boolean
  openTour: () => void
  closeTour: () => void
}

export const useHelpStore = create<HelpState>((set) => ({
  tourOpen: false,
  openTour: () => set({ tourOpen: true }),
  closeTour: () => set({ tourOpen: false }),
}))
