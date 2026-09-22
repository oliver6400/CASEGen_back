import { QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { queryClient } from './queryClient'
import { AppearanceManager } from '../features/appearance/components/AppearanceManager'

interface AppProvidersProps {
  children: ReactNode
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <QueryClientProvider client={queryClient}><AppearanceManager>{children}</AppearanceManager></QueryClientProvider>
  )
}
