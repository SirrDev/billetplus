import { useEffect, useRef, useState } from 'react';
import type { Map as CarteLeaflet, Marker } from 'leaflet';
import { Avis, ChampTexte } from '@/ui';
import { formaterCoordonnees, lireCoordonnees } from '../api';
import s from './organisateur.module.css';

type Leaflet = (typeof import('./carteLeaflet'))['L'];

const DAKAR: [number, number] = [14.6928, -17.4467];
const ZOOM_VILLE = 12;
const ZOOM_LIEU = 16;

/**
 * Position GPS d'un lieu : un clic sur la carte pose le repère, qui se déplace ensuite au
 * doigt ou à la souris. Le champ reste la source de la valeur : il suit la carte, permet de
 * coller des coordonnées et reste utilisable au clavier ou si la carte ne se charge pas.
 */
export function CartePosition({
  valeur,
  onChange,
  erreur,
}: {
  valeur: string;
  onChange: (valeur: string) => void;
  erreur?: string;
}) {
  const conteneur = useRef<HTMLDivElement>(null);
  const repere = useRef<Marker | null>(null);
  // Les écouteurs de Leaflet vivent aussi longtemps que la carte : toujours le dernier onChange.
  const surChangement = useRef(onChange);
  surChangement.current = onChange;
  const [carte, setCarte] = useState<{ L: Leaflet; map: CarteLeaflet } | null>(null);
  const [indisponible, setIndisponible] = useState(false);

  useEffect(() => {
    let annule = false;
    let map: CarteLeaflet | null = null;
    import('./carteLeaflet')
      .then(({ L }) => {
        if (annule || !conteneur.current) return;
        map = L.map(conteneur.current).setView(DAKAR, ZOOM_VILLE);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        }).addTo(map);
        map.on('click', (e) => {
          // wrap : une carte défilée au-delà de 180° reste dans les bornes acceptées par le backend.
          const { lat, lng } = e.latlng.wrap();
          surChangement.current(formaterCoordonnees(lat, lng));
        });
        setCarte({ L, map });
      })
      .catch(() => {
        if (!annule) setIndisponible(true);
      });
    return () => {
      annule = true;
      repere.current = null;
      map?.remove();
    };
  }, []);

  const position = lireCoordonnees(valeur);
  const latitude = position?.latitude;
  const longitude = position?.longitude;

  useEffect(() => {
    if (!carte) return;
    if (latitude === undefined || longitude === undefined) {
      repere.current?.remove();
      repere.current = null;
      return;
    }
    const { L, map } = carte;
    if (!repere.current) {
      const icon = L.divIcon({ className: s.repere, iconSize: [24, 24], iconAnchor: [12, 12] });
      const nouveau = L.marker([latitude, longitude], { draggable: true, icon, alt: 'Position du lieu' }).addTo(map);
      nouveau.on('dragend', () => {
        const { lat, lng } = nouveau.getLatLng().wrap();
        surChangement.current(formaterCoordonnees(lat, lng));
      });
      repere.current = nouveau;
    } else {
      repere.current.setLatLng([latitude, longitude]);
    }
    if (!map.getBounds().contains([latitude, longitude])) {
      map.setView([latitude, longitude], Math.max(map.getZoom(), ZOOM_LIEU));
    }
  }, [carte, latitude, longitude]);

  return (
    <>
      {indisponible ? (
        <Avis ton="attention">
          <p>La carte n'a pas pu se charger. Vous pouvez saisir les coordonnées ci-dessous.</p>
        </Avis>
      ) : (
        <div ref={conteneur} className={s.carte} role="group" aria-label="Carte pour placer le lieu" />
      )}
      <ChampTexte
        libelle="Coordonnées GPS"
        valeur={valeur}
        onChange={onChange}
        maxLength={60}
        aide="Touchez la carte pour placer le repère, puis faites-le glisser pour l'ajuster. Elles servent au lien « Voir sur la carte » des acheteurs."
        erreur={erreur}
      />
    </>
  );
}
