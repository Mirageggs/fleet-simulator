import { COLORES_ESTADO, ETIQUETAS_ESTADO } from './estados.js'

/** Leyenda flotante del mapa: ubicaciones y estados de camión. */
export default function Legend() {
  return (
    <div className="leyenda">
      <div className="leyenda-titulo">Leyenda</div>
      <div className="leyenda-fila">
        <span className="leyenda-punto" style={{ background: '#2f9e4f' }} />
        Punto de carga
      </div>
      <div className="leyenda-fila">
        <span className="leyenda-punto" style={{ background: '#e8590c' }} />
        Punto de descarga
      </div>
      <div className="leyenda-sep" />
      {Object.entries(ETIQUETAS_ESTADO).map(([estado, etiqueta]) => (
        <div className="leyenda-fila" key={estado}>
          <span
            className="leyenda-punto leyenda-camion"
            style={{ borderColor: COLORES_ESTADO[estado] }}
          />
          {etiqueta}
        </div>
      ))}
    </div>
  )
}
