import axios from 'axios'
import type { ApiProblem } from './apiTypes'

const messagesByCode: Record<string, string> = {
  CLASSIFIER_IN_USE: 'No se puede eliminar la clase o interfaz porque otro atributo o parámetro la utiliza como tipo. Cambia o elimina primero esas referencias.',
  CLASSIFIER_NAME_ALREADY_EXISTS: 'Ya existe una clase o interfaz con ese nombre dentro del mismo paquete.',
  OPTIMISTIC_LOCKING_FAILURE: 'El elemento fue modificado por otro usuario. Se actualizaron los datos.',
  CONCURRENT_MODIFICATION: 'El elemento fue modificado por otro usuario. Se actualizaron los datos.',
  CONCURRENT_MODEL_MODIFICATION: 'El elemento fue modificado por otro usuario. Se actualizaron los datos.',
}

export function getApiErrorMessage(error: unknown) {
  if (axios.isAxiosError<ApiProblem>(error)) {
    if (error.response?.status === 409) {
      const problem = error.response.data
      const message = messagesByCode[problem?.code ?? ''] ?? problem?.message ?? problem?.detail ?? problem?.title ?? 'Conflicto al completar la solicitud.'
      return `${problem?.code ?? 'HTTP_409'}: ${message}`
    }
    return (
      (error.response?.data?.code && messagesByCode[error.response.data.code]) ??
      error.response?.data?.detail ??
      error.response?.data?.message ??
      error.response?.data?.title ??
      'No fue posible completar la solicitud.'
    )
  }
  return error instanceof Error ? error.message : 'Ocurrió un error inesperado.'
}
