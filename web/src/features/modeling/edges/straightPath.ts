interface Point { x: number; y: number }

/**
 * Projects a UML relation as a single segment at any angle. V7.1 routing
 * metadata remains persisted, while stored waypoints no longer bend the line.
 */
export function straightPath(source: Point, target: Point) {
  return {
    path: `M ${source.x} ${source.y} L ${target.x} ${target.y}`,
    sourceToward: target,
    targetToward: source,
  }
}
