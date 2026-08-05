import { useEffect } from 'react'
import {
  Circle,
  CircleMarker,
  MapContainer,
  Marker,
  Polyline,
  TileLayer,
  Tooltip,
  useMap,
} from 'react-leaflet'
import L from 'leaflet'
import Legend from './Legend.jsx'
import { COLORES_ESTADO } from './estados.js'

/** Encuadra el mapa a toda la red al cargar los datos. */
function Encuadre({ red }) {
  const map = useMap()
  useEffect(() => {
    if (!red) return
    const puntos = []
    red.tramos.forEach((t) => t.puntos.forEach((p) => puntos.push(p)))
    red.cargas.forEach((u) => puntos.push([u.lat, u.lng]))
    red.descargas.forEach((u) => puntos.push([u.lat, u.lng]))
    if (puntos.length > 0) {
      map.fitBounds(L.latLngBounds(puntos).pad(0.12))
    }
  }, [red, map])
  return null
}

function iconoCamion(camion) {
  const color = COLORES_ESTADO[camion.estado] || '#ffb703'
  const numero = Number(camion.id.slice(-3)) || '?'
  return L.divIcon({
    className: 'camion-marcador',
    html: `<div class="camion-punto" style="--c:${color}">${numero}</div>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  })
}

/** Marcador de carga (verde) o descarga (naranja) con su radio si existe. */
function Ubicacion({ u, tipo }) {
  const color = tipo === 'carga' ? '#2f9e4f' : '#e8590c'
  return (
    <>
      {u.radio != null && (
        <Circle
          center={[u.lat, u.lng]}
          radius={u.radio}
          pathOptions={{ color, weight: 1.5, dashArray: '5 5', fillColor: color, fillOpacity: 0.08 }}
        />
      )}
      <CircleMarker
        center={[u.lat, u.lng]}
        radius={8}
        pathOptions={{ color: '#12161b', weight: 2, fillColor: color, fillOpacity: 1 }}
      >
        <Tooltip direction="top" offset={[0, -8]}>
          {tipo === 'carga' ? 'Carga' : 'Descarga'} · {u.nombre}
          {u.radio != null ? ` · radio ${u.radio} m` : ''}
        </Tooltip>
      </CircleMarker>
    </>
  )
}

export default function MapView({ red, sim }) {
  const centro =
    red.tramos[0]?.puntos[0] ||
    (red.cargas[0] ? [red.cargas[0].lat, red.cargas[0].lng] : [-9.53, -77.06])

  return (
    <div className="mapa-envoltura">
      <MapContainer center={centro} zoom={14} className="mapa" scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <Encuadre red={red} />

        {red.tramos.map((t) => (
          <Polyline
            key={t.id}
            positions={t.puntos}
            pathOptions={{ color: t.color, weight: 5, opacity: 0.9 }}
          >
            <Tooltip sticky>{t.nombre}</Tooltip>
          </Polyline>
        ))}

        {red.cargas.map((u, i) => (
          <Ubicacion key={`carga-${i}`} u={u} tipo="carga" />
        ))}
        {red.descargas.map((u, i) => (
          <Ubicacion key={`descarga-${i}`} u={u} tipo="descarga" />
        ))}

        {sim?.camiones
          ?.filter((c) => c.lat != null && c.lng != null)
          .map((c) => (
            <Marker
              key={c.id}
              position={[c.lat, c.lng]}
              icon={iconoCamion(c)}
              zIndexOffset={1000}
            >
              <Tooltip direction="top" offset={[0, -16]}>
                {c.id} · {c.velocidadKmh} km/h · {c.estado.replace('_', ' ')}
              </Tooltip>
            </Marker>
          ))}
      </MapContainer>
      <Legend />
    </div>
  )
}
