/*
 * Leaflet et sa feuille de style, chargés à la demande par CartePosition : seul le formulaire
 * de l'organisateur s'en sert. Ce module donne son nom au morceau exclu du précache
 * (vite.config.ts, globIgnores) : ne pas le renommer sans mettre la règle à jour.
 */
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

export { L };
