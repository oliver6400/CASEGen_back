import { httpClient } from '../../../api/httpClient'
import type { PagedAiMessageResponse, SendAiMessageRequest, SendAiMessageResponse } from '../types/aiMessage'

const messagesPath = (projectId: string, sessionId: string) =>
  `/api/projects/${projectId}/ai/sessions/${sessionId}/messages`

export interface SpeechTranscriptionResponse {
  text: string
}

export const aiChatApi = {
  getMessages: async (projectId: string, sessionId: string, page = 0, size = 50) =>
    (await httpClient.get<PagedAiMessageResponse>(messagesPath(projectId, sessionId), { params: { page, size } })).data,
  sendMessage: async (projectId: string, sessionId: string, request: SendAiMessageRequest) =>
    (await httpClient.post<SendAiMessageResponse>(messagesPath(projectId, sessionId), request)).data,
  transcribeAudio: async (audio: Blob) => {
    const formData = new FormData()
    formData.append('audio', audio, 'recording.wav')
    return (await httpClient.post<SpeechTranscriptionResponse>(
      '/api/ai/speech/transcribe', formData,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    )).data
  },
}
