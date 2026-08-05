import { COLORES_ESTADO, ETIQUETAS_ESTADO } from './estados.js'

/** Panel lateral con el estado en vivo de cada camión. */
export default function FleetPanel({ sim }) {
  if (!sim?.camiones?.length) {
    return (
      <div className="panel">
        <h2>Flota</h2>
        <p className="texto-suave">
          Sin simulación activa. Pulsa <strong>Iniciar</strong> para poner los 5
          camiones en movimiento; puedes fijar una semilla para reproducir la
          misma corrida.
        </p>
      </div>
    )
  }

  return (
    <div className="panel">
      <h2>Flota</h2>
      <ul className="lista-camiones">
        {sim.camiones.map((c) => (
          <li key={c.id} className="camion-fila">
            <div className="camion-cabecera">
              <span
                className="camion-insignia"
                style={{ '--c': COLORES_ESTADO[c.estado] || '#ffb703' }}
              >
                {c.id}
              </span>
              <span className="camion-velocidad">
                {c.velocidadKmh.toFixed(1)} <small>km/h</small>
              </span>
            </div>
            <div className="barra">
              <div
                className="barra-relleno"
                style={{
                  width: `${Math.min(100, c.progresoPct)}%`,
                  background: COLORES_ESTADO[c.estado] || '#ffb703',
                }}
              />
            </div>
            <div className="camion-detalle">
              <span>{ETIQUETAS_ESTADO[c.estado] || c.estado}</span>
              {c.origen !== '-' && (
                <span className="texto-suave">
                  {c.origen} → {c.destino}
                </span>
              )}
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
