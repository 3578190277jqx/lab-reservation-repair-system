import { describe, expect, it } from 'vitest'
import { railBounds, nextRailPosition } from '../src/utils/horizontal-rail'

describe('native equipment rail boundaries', () => {
  it('disables both directions when everything fits, including an empty rail', () => {
    expect(railBounds(0, 1200, 1200)).toEqual({ start: true, end: true, maximum: 0 })
    expect(railBounds(0, 0, 0)).toEqual({ start: true, end: true, maximum: 0 })
  })
  it('handles fractional endpoints and browser elastic overscroll', () => {
    expect(railBounds(-8, 320, 1200)).toEqual({ start: true, end: false, maximum: 880 })
    expect(railBounds(879.7, 320, 1200).end).toBe(true)
    expect(railBounds(300, 320, 1200)).toEqual({ start: false, end: false, maximum: 880 })
  })
  it('moves to actual card starts after arbitrary touch scrolling', () => {
    expect(nextRailPosition(120, [0, 340, 680, 1020], 800, 1)).toBe(340)
    expect(nextRailPosition(470, [0, 340, 680, 1020], 800, -1)).toBe(340)
  })
  it('clamps the final card to the reachable endpoint and can move back', () => {
    expect(nextRailPosition(680, [0, 340, 680, 1020], 800, 1)).toBe(800)
    expect(nextRailPosition(800, [0, 340, 680, 1020], 800, -1)).toBe(680)
    expect(nextRailPosition(800, [0, 340, 680, 1020], 800, 1)).toBe(800)
  })
  it('keeps empty rails and the first position within bounds', () => {
    expect(nextRailPosition(0, [], 0, 1)).toBe(0)
    expect(nextRailPosition(0, [0, 300], 180, -1)).toBe(0)
  })
})
