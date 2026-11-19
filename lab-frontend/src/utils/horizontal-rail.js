export function railBounds(position, viewport, content) {
  const maximum = Math.max(0, content - viewport)
  return { start: position <= 1, end: position >= maximum - 1, maximum }
}

export function nextRailPosition(position, starts, maximum, direction) {
  const stops = [...new Set([0, ...starts, maximum].map(value => Math.max(0, Math.min(maximum, value))))].sort((a, b) => a - b)
  return direction > 0
    ? stops.find(value => value > position + 1) ?? maximum
    : stops.findLast(value => value < position - 1) ?? 0
}
