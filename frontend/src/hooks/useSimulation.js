import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api.js'

/**
 * Estado de la aplicación: red vial, simulación en vivo y reporte.
 *
 * Canal en tiempo real: SSE (EventSource) sobre /api/simulation/stream.
 * Si SSE falla (proxies, red), cae automáticamente a polling de 1 s sobre
 * GET /api/simulation, y el chip del encabezado indica el canal activo.
 */
export function useSimulation() {
  const [red, setRed] = useState(null)
  const [cargandoRed, setCargandoRed] = useState(true)
  const [errorRed, setErrorRed] = useState(null)

  const [sim, setSim] = useState(null)
  const [reporte, setReporte] = useState(null)
  const [error, setError] = useState(null)
  const [iniciando, setIniciando] = useState(false)
  const [transporte, setTransporte] = useState('sse')

  const streamRef = useRef(null)
  const pollRef = useRef(null)

  const cargarRed = useCallback(async () => {
    setCargandoRed(true)
    setErrorRed(null)
    try {
      setRed(await api.red())
    } catch (e) {
      setErrorRed(e.message)
    } finally {
      setCargandoRed(false)
    }
  }, [])

  const cerrarCanales = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.close()
      streamRef.current = null
    }
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [])

  const pedirReporte = useCallback(async () => {
    try {
      setReporte(await api.reporte())
      setError(null)
    } catch (e) {
      setError(e.message)
    }
  }, [])

  const iniciarPolling = useCallback(() => {
    setTransporte('polling')
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      try {
        const vista = await api.simulacion()
        if (vista?.estado && vista.estado !== 'SIN_SIMULACION') setSim(vista)
        if (vista?.estado === 'FINALIZADA') {
          clearInterval(pollRef.current)
          pollRef.current = null
          pedirReporte()
        }
      } catch {
        // el backend puede estar reiniciando; se reintenta en el siguiente ciclo
      }
    }, 1000)
  }, [pedirReporte])

  const abrirStream = useCallback(() => {
    cerrarCanales()
    setTransporte('sse')
    const stream = new EventSource(api.streamUrl)
    streamRef.current = stream
    stream.addEventListener('estado', (ev) => setSim(JSON.parse(ev.data)))
    stream.addEventListener('fin', (ev) => {
      setSim(JSON.parse(ev.data))
      pedirReporte()
      stream.close()
      streamRef.current = null
    })
    stream.onerror = () => {
      stream.close()
      streamRef.current = null
      iniciarPolling()
    }
  }, [cerrarCanales, iniciarPolling, pedirReporte])

  const iniciar = useCallback(
    async (semilla) => {
      setIniciando(true)
      setError(null)
      setReporte(null)
      try {
        const vista = await api.iniciar(semilla)
        setSim(vista)
        abrirStream()
      } catch (e) {
        setError(e.message)
      } finally {
        setIniciando(false)
      }
    },
    [abrirStream],
  )

  useEffect(() => {
    cargarRed()
    // Si ya hay una simulación en el backend (p. ej. tras recargar la página),
    // se reengancha al estado actual.
    ;(async () => {
      try {
        const vista = await api.simulacion()
        if (vista?.estado === 'EN_CURSO') {
          setSim(vista)
          abrirStream()
        } else if (vista?.estado === 'FINALIZADA') {
          setSim(vista)
          pedirReporte()
        }
      } catch {
        // sin backend todavía: la pantalla de red mostrará el error
      }
    })()
    return cerrarCanales
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return {
    red,
    cargandoRed,
    errorRed,
    recargarRed: cargarRed,
    sim,
    reporte,
    error,
    iniciando,
    transporte,
    iniciar,
    pedirReporte,
  }
}
