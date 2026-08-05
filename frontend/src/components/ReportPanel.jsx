/** Reporte de velocidades: tabla por camión, resumen de flota y explicación. */
export default function ReportPanel({ reporte, enCurso, haySim, onPedir }) {
  return (
    <div className="panel">
      <div className="panel-cabecera">
        <h2>Reporte de velocidades</h2>
        {haySim && (
          <button className="boton-secundario" onClick={onPedir}>
            {enCurso ? 'Ver parcial' : 'Actualizar'}
          </button>
        )}
      </div>

      {!reporte && (
        <p className="texto-suave">
          {haySim
            ? enCurso
              ? 'La simulación está en curso; puedes pedir un reporte parcial o esperar al final (se genera solo).'
              : 'Generando el reporte…'
            : 'El reporte se genera al finalizar la simulación (o bajo demanda con la simulación en curso).'}
        </p>
      )}

      {reporte && (
        <>
          <p className="reporte-meta">
            {reporte.parcial ? 'Parcial' : 'Final'} · semilla {reporte.semilla} ·
            fuente: {reporte.explicacion.fuente}
          </p>
          <table className="tabla">
            <thead>
              <tr>
                <th>Camión</th>
                <th>N</th>
                <th>Mín</th>
                <th>Máx</th>
                <th>Prom</th>
                <th>km</th>
              </tr>
            </thead>
            <tbody>
              {reporte.camiones.map((c) => (
                <tr key={c.id}>
                  <td>{c.id}</td>
                  <td>{c.muestras}</td>
                  <td>{c.velMinKmh.toFixed(1)}</td>
                  <td>{c.velMaxKmh.toFixed(1)}</td>
                  <td className="tabla-destacado">{c.velPromedioKmh.toFixed(2)}</td>
                  <td>{c.distanciaKm.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Flota</td>
                <td colSpan="3">{reporte.flota.camionesConDatos} con datos</td>
                <td className="tabla-destacado">
                  {reporte.flota.promedioKmh.toFixed(2)}
                </td>
                <td />
              </tr>
            </tfoot>
          </table>

          <div className="explicacion">
            {reporte.explicacion.texto.split('\n').map((linea, i) => (
              <p key={i}>{linea}</p>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
