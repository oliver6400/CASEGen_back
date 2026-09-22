import { useQuery } from '@tanstack/react-query'
import { projectApi } from '../../projects/api/projectApi'

export function useProjectPermission(projectId: string | undefined) {
  return useQuery({
    queryKey: ['projects', projectId, 'access'],
    queryFn: () => projectApi.access(projectId!),
    enabled: Boolean(projectId),
    staleTime: 30_000,
  })
}
