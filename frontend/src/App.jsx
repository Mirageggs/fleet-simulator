import { useState } from 'react'
import { useSimulation } from './hooks/useSimulation.js'
import MapView from './components/MapView.jsx'
import FleetPanel from './components/FleetPanel.jsx'
import ReportPanel from './components/ReportPanel.jsx'

export default function App() {
  const {
    red,
    cargandoRed,
    errorRed,
    recargarRed,
    sim,
    reporte,
    error,
    iniciando,
    transporte,
    iniciar,
    pedirReporte,
  } = useSimulation()

  const [semilla, setSemilla] = useState('')
  const enCurso = sim?.estado === 'EN_CURSO'
  const haySim = Boolean(sim && sim.estado !== 'SIN_SIMULACION')
  const operativo = Boolean(red?.validacion?.operativo)

  return (
    <div className="app">
      <header className="cabecera">
        <div className="marca">
          <h1>Simulador de Flota</h1>
          <span className="marca-sub">Despacho · red vial minera</span>
        </div>

        <div className="controles">
          {haySim && (
            <span className={`chip ${enCurso ? 'chip-activo' : 'chip-fin'}`}>
              {enCurso ? 'EN CURSO' : 'FINALIZADA'} · {sim.transcurridoSeg}s ·
              semilla {sim.semilla}
            </span>
          )}
          {haySim && (
            <span className="chip" title="Canal de actualización en tiempo real">
              {transporte === 'sse' ? 'SSE' : 'Polling'}
            </span>
          )}
          {red?.config?.escalaTiempo > 1 && (
            <span
              className="chip"
              title="Escala de tiempo: la simulación avanza más rápido que el reloj real (APP_TIME_SCALE); no altera velocidades ni estadísticas"
            >
              ×{red.config.escalaTiempo}
            </span>
          )}
          <input
            className="entrada"
            type="number"
            placeholder="Semilla (opcional)"
            value={semilla}
            onChange={(e) => setSemilla(e.target.value)}
            title="Con la misma semilla la corrida se repite exactamente igual"
          />
          <button
            className="boton"
            disabled={iniciando || cargandoRed || !operativo}
            onClick={() => iniciar(semilla)}
          >
            {iniciando ? 'Iniciando…' : haySim ? 'Reiniciar' : 'Iniciar'}
          </button>
        </div>
      </header>

      {error && <div className="aviso aviso-error">{error}</div>}

      <div className="cuerpo">
        <aside className="lateral">
          <FleetPanel sim={sim} />
          <ReportPanel
            reporte={reporte}
            enCurso={enCurso}
            haySim={haySim}
            onPedir={pedirReporte}
          />

          {red?.validacion && (
            <div className="panel">
              <h2>Validación de datos</h2>
              <p className="texto-suave">
                {red.tramos.length} tramos válidos · {red.cargas.length} cargas ·{' '}
                {red.descargas.length} descargas · {red.grafo.componentesConexos}{' '}
                componente(s) conexo(s) · orden {red.validacion.ordenCoordenadas}
              </p>
              {red.validacion.errores.map((e, i) => (
                <p className="item-error" key={`e${i}`}>
                  ✕ {e}
                </p>
              ))}
              {red.validacion.advertencias.map((a, i) => (
                <p className="item-advertencia" key={`a${i}`}>
                  ! {a}
                </p>
              ))}
              {red.validacion.errores.length === 0 &&
                red.validacion.advertencias.length === 0 && (
                  <p className="texto-suave">Sin observaciones.</p>
                )}
            </div>
          )}

          {sim?.decisiones?.length > 0 && (
            <div className="panel">
              <h2>Decisiones de la simulación</h2>
              {sim.decisiones.map((d, i) => (
                <p className="item-decision" key={i}>
                  → {d}
                </p>
              ))}
            </div>
          )}
        </aside>

        <main className="principal">
          {cargandoRed && <div className="estado-centro">Cargando red vial…</div>}
          {!cargandoRed && errorRed && (
            <div className="estado-centro">
              <p className="item-error">✕ {errorRed}</p>
              <button className="boton" onClick={recargarRed}>
                Reintentar
              </button>
            </div>
          )}
          {!cargandoRed && !errorRed && red && <MapView red={red} sim={sim} />}
        </main>
      </div>
    </div>
  )
}
