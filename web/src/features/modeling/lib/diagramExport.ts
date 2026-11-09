import { toPng, toSvg } from 'html-to-image'
import type { DiagramNode } from '../types/uml'

export type DiagramExportFormat = 'png' | 'svg'

export interface DiagramExportBounds {
  x: number
  y: number
  width: number
  height: number
}

const DEFAULT_PADDING = 72
const MAX_FILE_NAME_LENGTH = 120
const MAX_RASTER_DIMENSION = 16_000

export function calculateDiagramExportBounds(
  nodes: Pick<DiagramNode, 'x' | 'y' | 'width' | 'height'>[],
  padding = DEFAULT_PADDING,
): DiagramExportBounds {
  if (nodes.length === 0) throw new Error('El diagrama no contiene elementos para exportar.')
  if (!Number.isFinite(padding) || padding < 0) throw new Error('El padding de exportación no es válido.')

  const minX = Math.min(...nodes.map((node) => node.x))
  const minY = Math.min(...nodes.map((node) => node.y))
  const maxX = Math.max(...nodes.map((node) => node.x + node.width))
  const maxY = Math.max(...nodes.map((node) => node.y + node.height))
  if (![minX, minY, maxX, maxY].every(Number.isFinite) || maxX <= minX || maxY <= minY) {
    throw new Error('La geometría del diagrama no es válida para exportar.')
  }
  return {
    x: minX - padding,
    y: minY - padding,
    width: Math.ceil(maxX - minX + padding * 2),
    height: Math.ceil(maxY - minY + padding * 2),
  }
}

export function safeDiagramFileName(name: string, format: DiagramExportFormat) {
  const printable = [...name].map((character) => character.charCodeAt(0) < 32 ? '-' : character).join('')
  const base = printable.normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[<>:"/\\|?*]/g, '-')
    .replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^[.\s-]+|[.\s-]+$/g, '')
    .slice(0, MAX_FILE_NAME_LENGTH) || 'diagrama-uml'
  return `${base}.${format}`
}

export function createDiagramExportPlan(
  nodes: Pick<DiagramNode, 'x' | 'y' | 'width' | 'height'>[],
  padding = DEFAULT_PADDING,
) {
  const bounds = calculateDiagramExportBounds(nodes, padding)
  return {
    ...bounds,
    transform: `translate(${-bounds.x}px, ${-bounds.y}px) scale(1)`,
  }
}

export async function exportDiagramImage({
  viewport,
  nodes,
  name,
  format,
}: {
  viewport: HTMLElement
  nodes: Pick<DiagramNode, 'x' | 'y' | 'width' | 'height'>[]
  name: string
  format: DiagramExportFormat
}) {
  const plan = createDiagramExportPlan(nodes)
  const pixelRatio = format === 'png'
    ? Math.max(1, Math.min(2, MAX_RASTER_DIMENSION / plan.width, MAX_RASTER_DIMENSION / plan.height))
    : 1
  viewport.classList.add('diagram-exporting')
  try {
    const options = {
      backgroundColor: '#ffffff',
      cacheBust: true,
      filter: includeInExport,
      height: plan.height,
      pixelRatio,
      style: {
        height: `${plan.height}px`,
        transform: plan.transform,
        transformOrigin: 'top left',
        width: `${plan.width}px`,
      },
      width: plan.width,
    }
    const dataUrl = format === 'png'
      ? await toPng(viewport, options)
      : await toSvg(viewport, options)
    downloadDataUrl(dataUrl, safeDiagramFileName(name, format))
  } finally {
    viewport.classList.remove('diagram-exporting')
  }
}

function includeInExport(node: HTMLElement) {
  if (!(node instanceof Element)) return true
  return !node.hasAttribute('data-diagram-export-ignore')
    && !node.classList.contains('react-flow__resize-control')
}

function downloadDataUrl(dataUrl: string, fileName: string) {
  const anchor = document.createElement('a')
  anchor.href = dataUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}
