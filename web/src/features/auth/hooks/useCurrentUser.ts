import { useQuery } from '@tanstack/react-query'
import { authApi } from '../api/authApi'
import { useAuthStore } from '../authStore'

export function useCurrentUser() {
  const accessToken = useAuthStore((state) => state.accessToken)
  return useQuery({
    queryKey: ['auth', 'me'],
    queryFn: authApi.me,
    enabled: Boolean(accessToken),
    retry: false,
    staleTime: 60_000,
  })
}
