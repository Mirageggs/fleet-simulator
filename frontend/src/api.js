// Cliente HTTP mínimo hacia el backend. La URL base se configura con
// VITE_API_URL (Vercel) y por defecto apunta al backend local.
const API = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

async function pedir(ruta, opciones) {
  let respuesta
  try {
    respuesta = await fetch(`${API}${ruta}`, opciones)
  } catch {
    throw new Error(`No se pudo conectar con el backend (${API}). ¿Está en ejecución?`)
  }
  const cuerpo = await respuesta.json().catch(() => null)
  if (!respuesta.ok) {
    throw new Error(cuerpo?.mensaje || `Error ${respuesta.status} del backend`)
  }
  return cuerpo
}

export const api = {
  base: API,
  streamUrl: `${API}/api/simulation/stream`,
  red: () => pedir('/api/network'),
  simulacion: () => pedir('/api/simulation'),
  iniciar: (semilla) =>
    pedir('/api/simulation/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(
        semilla === '' || semilla == null ? {} : { semilla: Number(semilla) },
      ),
    }),
  reporte: () => pedir('/api/simulation/report'),
}
