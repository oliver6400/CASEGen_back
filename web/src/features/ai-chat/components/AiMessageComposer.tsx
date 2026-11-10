import { useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { ArrowUp, ImagePlus, LoaderCircle, Mic, Square, X } from 'lucide-react'
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
  onSend: (content: string, skill: AiSkill, imageUrl?: string | null) => void
}) {
  const [content, setContent] = useState('')
  const [skill, setSkill] = useState<AiSkill>('GENERAL')
  const [recording, setRecording] = useState(false)
  const [transcribing, setTranscribing] = useState(false)
  const [speechError, setSpeechError] = useState('')
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [imageError, setImageError] = useState('')
  const capture = useRef<AudioCapture | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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
    if ((!trimmed && !imageUrl) || disabled || processing) return
    const finalContent = trimmed || 'Analiza la imagen adjunta y extrae las clases al lienzo.'
    onSend(finalContent, skill, imageUrl)
    setContent('')
    setImageUrl(null)
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit() }
  }

  const onFileSelected = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (file.size > 10 * 1024 * 1024) {
      setImageError('La imagen no puede superar 10 MB.')
      return
    }
    setImageError('')
    const reader = new FileReader()
    reader.onload = (event) => {
      const dataUrl = event.target?.result as string
      setImageUrl(dataUrl)
    }
    reader.readAsDataURL(file)
    e.target.value = ''
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

  return <div className="border-t border-[var(--border)] pt-1.5">
    {processing && <p className="mb-1 text-[11px] font-medium text-indigo-600">CASEGen IA está respondiendo y procesando…</p>}
    {recording && <p aria-live="polite" className="mb-1 text-[11px] font-medium text-red-600">Grabando… pulsa el micrófono para terminar (máx. 60 s).</p>}
    {transcribing && <p aria-live="polite" className="mb-1 text-[11px] font-medium text-indigo-600">Transcribiendo audio sin conexión…</p>}
    {speechError && <p role="alert" className="mb-1 text-[11px] text-red-600">{speechError}</p>}
    {imageError && <p role="alert" className="mb-1 text-[11px] text-red-600">{imageError}</p>}

    {imageUrl && (
      <div className="relative mb-2 inline-flex items-center gap-2 rounded-lg border border-indigo-200 bg-indigo-50/70 p-1.5">
        <img src={imageUrl} alt="Diagrama adjunto" className="h-16 w-auto max-w-[120px] rounded object-cover shadow-sm" />
        <div className="text-[11px] text-indigo-900 pr-5">
          <span className="font-semibold block">Imagen adjunta</span>
          <span className="text-slate-500 text-[10px]">Se extraerán las clases al lienzo</span>
        </div>
        <button
          type="button"
          onClick={() => setImageUrl(null)}
          className="absolute -top-1.5 -right-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-slate-700 text-white shadow transition hover:bg-red-600"
          title="Eliminar imagen"
        >
          <X size={12} />
        </button>
      </div>
    )}

    <input
      type="file"
      ref={fileInputRef}
      accept="image/*,.png,.jpg,.jpeg,.webp,.jfif"
      className="hidden"
      onChange={onFileSelected}
    />

    <textarea
      className="min-h-14 w-full resize-none rounded-lg border border-[var(--border)] bg-[var(--surface)] px-2.5 py-2 text-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
      disabled={disabled || processing || recording || transcribing}
      maxLength={MAX_LENGTH}
      onChange={(event) => setContent(event.target.value)}
      onKeyDown={onKeyDown}
      placeholder={imageUrl ? "Describe detalles opcionales o presiona Enviar…" : "Escribe una pregunta o adjunta un diagrama..."}
      value={content}
    />

    <div className="mt-1 flex items-end justify-between gap-2">
      <div className="min-w-0">
        <label className="flex items-center gap-1.5 text-[11px] font-medium text-slate-600">Modo IA
          <select aria-label="Modo IA" className="min-w-0 rounded-md border border-[var(--border)] bg-[var(--surface)] px-2 py-1 text-xs" disabled={disabled || processing || recording || transcribing} onChange={(event) => setSkill(event.target.value as AiSkill)} value={skill}>
            <option value="GENERAL">General</option>
            <option value="UML_REVIEWER">Revisor UML</option>
            <option value="DATABASE_REVIEWER">Revisor BD</option>
          </select>
        </label>
        <span className="mt-0.5 block text-[9px] text-slate-400">{content.length}/{MAX_LENGTH} · Shift+Enter: nueva línea</span>
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        <button
          aria-label="Adjuntar imagen de diagrama"
          className={`inline-flex h-9 w-9 items-center justify-center rounded-lg border border-[var(--border)] bg-[var(--surface)] transition focus:outline-none focus:ring-4 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-50 ${
            imageUrl ? 'border-indigo-500 text-indigo-600 bg-indigo-50' : 'text-slate-600 hover:border-indigo-300 hover:text-indigo-600'
          }`}
          disabled={disabled || processing || recording || transcribing}
          onClick={() => fileInputRef.current?.click()}
          title="Adjuntar imagen de diagrama (PNG, JPG, WEBP)"
          type="button"
        >
          <ImagePlus aria-hidden="true" size={16} />
        </button>

        <button aria-label={recording ? 'Detener grabación' : 'Dictar con el micrófono'} className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-[var(--border)] bg-[var(--surface)] text-slate-600 transition hover:border-indigo-300 hover:text-indigo-600 focus:outline-none focus:ring-4 focus:ring-indigo-100 disabled:cursor-not-allowed disabled:opacity-50" disabled={disabled || processing || transcribing} onClick={() => recording ? void stopRecording() : void startRecording()} title={recording ? 'Detener grabación' : 'Dictar con el micrófono'} type="button">
          {recording ? <Square aria-hidden="true" size={16} /> : <Mic aria-hidden="true" size={16} />}
        </button>

        <button aria-label={processing ? 'CASEGen IA está respondiendo' : 'Enviar mensaje'} className="inline-flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-sm transition hover:-translate-y-0.5 hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:translate-y-0 disabled:cursor-not-allowed disabled:bg-indigo-300" disabled={disabled || processing || recording || transcribing || (!content.trim() && !imageUrl)} onClick={submit} title={processing ? 'Procesando…' : 'Enviar'} type="button">
          {processing ? <LoaderCircle className="animate-spin" size={17} /> : <ArrowUp aria-hidden="true" size={18} strokeWidth={2.5} />}
        </button>
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
