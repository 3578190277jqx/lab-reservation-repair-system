/** Small standards-shaped storage fake for Node-based authentication tests. */
export class MemoryStorage {
  constructor() {
    this.values = new Map()
  }

  get length() {
    return this.values.size
  }

  key(index) {
    return [...this.values.keys()][index] ?? null
  }

  getItem(key) {
    return this.values.get(String(key)) ?? null
  }

  setItem(key, value) {
    this.values.set(String(key), String(value))
  }

  removeItem(key) {
    this.values.delete(String(key))
  }

  clear() {
    this.values.clear()
  }
}

export function installStorage(vi) {
  const sessionStorage = new MemoryStorage()
  const localStorage = new MemoryStorage()
  const window = new EventTarget()
  Object.assign(window, { sessionStorage, localStorage })
  vi.stubGlobal('sessionStorage', sessionStorage)
  vi.stubGlobal('localStorage', localStorage)
  vi.stubGlobal('window', window)
  return { sessionStorage, localStorage, window }
}
