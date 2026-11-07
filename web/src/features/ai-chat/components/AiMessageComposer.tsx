import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Mic, Square } from 'lucide-react'
import { aiChatApi } from '../api/aiChatApi'
import type { AiSkill } from '../types/aiMessage'

const MAX_LENGTH = 8000
const MAX_RECORDING_MS = 60_000

interface AudioCapture {
  context: AudioContext
  stream: MediaStream
  source: MediaStreamAudioSourceNode
  processor: ScriptProcessorNode
  chunks: Float32Array[]
  sampleRate: number
  timeout: number
}

export function AiMessageComposer({ disabled, processing, onSend }: {
  disabled: boolean
  processing: boolean
  onSend: (content: string, skill: AiSkill) => void
}) {
  const [content, setContent] = useState('')
  const [skill, setSkill] = useState<AiSkill>('GENERAL')
  const [recording, setRecording] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [speechError, setSpeechError] = useState('')
  const capture = useRef<AudioCapture | null>(null)

  useEffect(() => () => {
    const activeCapture = capture.current
    if (!activeCapture) return
    window.clearTimeout(activeCapture.timeout)
    activeCapture.processor.disconnect()
    activeCapture.source.disconnect()
    activeCapture.stream.getTracks().forEach((track) => track.stop())
    if (activeCapture.context.state !== 'closed') void activeCapture.context.close()
    capture.current = null
  }, [])

  const submit = () => {
    const trimmed = content.trim()
    if (!trimmed || disabled || processing) return
    onSend(trimmed, skill)
    setContent('')
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit() }
  }

  const stopRecording = async () => {
    const activeCapture = capture.current
    if (!activeCapture) return
    capture.current = null
    window.clearTimeout(activeCapture.timeout)
    activeCapture.processor.disconnect()
    activeCapture.source.disconnect()
    activeCapture.stream.getTracks().forEach((track) => track.stop())
    setRecording(false)
    await activeCapture.context.close()

    const audio = encodeWav(activeCapture.chunks, activeCapture.sampleRate)
    if (audio.size === 44) {
      setSpeechError('No se capturó audio. Inténtalo de nuevo.')
      return
    }

    setTranscribing(true)
    setSpeechError('')
    try {
      const { text } = await aiChatApi.transcribeAudio(audio)
      const transcript = text.trim()
      if (!transcript) {
        setSpeechError('No se reconoció voz en la grabación.')
        return
      }
      setContent((current) => {
        const separator = current.trim() ? ' ' : ''
        return `${current}${separator}${transcript}`.slice(0, MAX_LENGTH)
      })
    } catch (error) {
      setSpeechError(getSpeechError(error))
    } finally {
      setTranscribing(false)
    }
  }

  const startRecording = async () => {
    setSpeechError('')
    if (!navigator.mediaDevices?.getUserMedia || typeof AudioContext === 'undefined') {
      setSpeechError('Este navegador no permite grabar audio. Usa una conexión segura e inténtalo en otro navegador.')
      return
    }

    let stream: MediaStream | undefined
    let context: AudioContext | undefined
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
      })
      context = new AudioContext()
      await context.resume()
      const source = context.createMediaStreamSource(stream)
      const processor = context.createScriptProcessor(4096, 1, 1)
      const chunks: Float32Array[] = []
      processor.onaudioprocess = (event) => {
        chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)))
      }
      source.connect(processor)
      processor.connect(context.destination)
      const activeCapture = {
        context, stream, source, processor, chunks, sampleRate: context.sampleRate,
        timeout: window.setTimeout(() => { void stopRecording() }, MAX_RECORDING_MS),
      }
      capture.current = activeCapture
      setRecording(true)
    } catch {
      stream?.getTracks().forEach((track) => track.stop())
      if (context && context.state !== 'closed') await context.close()
      setSpeechError('No se pudo acceder al micrófono. Revisa los permisos del navegador.')
    }
  }

  return <div className="border-t border-[var(--border)] pt-3">
    {processing && <p className="mb-2 text-xs font-medium text-indigo-600">CASEGen IA está respondiendo…</p>}
    {recording && <p aria-live="polite" className="mb-2 text-xs font-medium text-red-600">Grabando… pulsa el micrófono para terminar (máx. 60 s).</p>}
    {transcribing && <p aria-live="polite" className="mb-2 text-xs font-medium text-indigo-600">Transcribiendo audio sin conexión…</p>}
    {speechError && <p role="alert" className="mb-2 text-xs text-red-600">{speechError}</p>}
    <label className="mb-2 block text-xs font-medium text-slate-600">Modo IA
      <select aria-label="Modo IA" className="ml-2 rounded-md border border-[var(--border)] bg-[var(--surface)] px-2 py-1 text-xs" disabled={disabled || processing} onChange={(event) => setSkill(event.target.value as AiSkill)} value={skill}>
        <option value="GENERAL">General</option>
        <option value="UML_REVIEWER">Revisor UML</option>
        <option value="DATABASE_REVIEWER">Revisor BD</option>
      </select>
    </label>
    <textarea className="min-h-24 w-full resize-y rounded-lg border border-[var(--border)] bg-[var(--surface)] p-3 text-sm outline-none focus:border-indigo-400" disabled={disabled || processing || recording || transcribing} maxLength={MAX_LENGTH} onChange={(event) => setContent(event.target.value)} onKeyDown={onKeyDown} placeholder="Escribe una pregunta…" value={content} />
    <div className="mt-2 flex items-center justify-between gap-2">
      <span className="text-[10px] text-slate-400">{content.length}/{MAX_LENGTH} · Shift+Enter para nueva línea</span>
      <div className="flex items-center gap-2">
        <button aria-label={recording ? 'Detener grabación' : 'Dictar con el micrófono'} className="toolbar-button" disabled={disabled || processing || transcribing} onClick={() => recording ? void stopRecording() : void startRecording()} title={recording ? 'Detener grabación' : 'Dictar con el micrófono'} type="button">
          {recording ? <Square aria-hidden="true" size={16} /> : <Mic aria-hidden="true" size={16} />}
        </button>
        <button className="primary-button" disabled={disabled || processing || recording || transcribing || !content.trim()} onClick={submit} type="button">Enviar</button>
      </div>
    </div>
  </div>
}

function encodeWav(chunks: Float32Array[], sampleRate: number) {
  const sampleCount = chunks.reduce((total, chunk) => total + chunk.length, 0)
  const buffer = new ArrayBuffer(44 + sampleCount * 2)
  const view = new DataView(buffer)
  writeAscii(view, 0, 'RIFF')
  view.setUint32(4, 36 + sampleCount * 2, true)
  writeAscii(view, 8, 'WAVE')
  writeAscii(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeAscii(view, 36, 'data')
  view.setUint32(40, sampleCount * 2, true)
  let offset = 44
  for (const chunk of chunks) {
    for (const sample of chunk) {
      const clamped = Math.max(-1, Math.min(1, sample))
      view.setInt16(offset, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true)
      offset += 2
    }
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

function writeAscii(view: DataView, offset: number, value: string) {
  for (let index = 0; index < value.length; index += 1) {
    view.setUint8(offset + index, value.charCodeAt(index))
  }
}

function getSpeechError(error: unknown) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = error.response
    if (typeof response === 'object' && response !== null && 'data' in response) {
      const data = response.data
      if (typeof response === 'object' && response !== null && 'status' in response
        && response.status === 503) {
        return 'El modelo de voz sin conexión no está instalado o configurado en el servidor.'
      }
      if (typeof data === 'object' && data !== null && 'detail' in data
        && typeof data.detail === 'string') return data.detail
      if (typeof data === 'object' && data !== null && 'message' in data
        && typeof data.message === 'string') return data.message
    }
  }
  return 'No se pudo transcribir el audio. Comprueba que el modelo de voz esté instalado.'
}
