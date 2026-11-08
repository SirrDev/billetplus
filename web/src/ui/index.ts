/*
 * Point d'entrée UNIQUE des composants d'interface. Une page n'importe jamais un
 * fichier interne de src/ui/ (ni un *.module.css) : uniquement depuis '@/ui'.
 */
export { Affiche } from './Affiche';
export {
  BarreAction,
  CompteARebours,
  LigneCategorie,
  Panneau,
  PasseBillet,
  Recapitulatif,
  SelecteurQuantite,
  type LigneRecap,
} from './Billetterie';
export { Bouton, BoutonLien, LienRetour } from './Bouton';
export { Cadre } from './Cadre';
export { CompteursControle, EcranControle, PanneauVerdict, Viseur } from './Controle';
export { CarteEvenement, EvenementALaUne, GrilleEvenements, type ResumeEvenement } from './CarteEvenement';
export { ChampChoix, ChampRecherche, ChampTelephone, ChampTexte, ChoixCartes, type OptionCarte } from './Formulaire';
export { Filtres } from './Filtres';
export { Icone, type NomIcone } from './Icone';
export {
  codeBillet,
  motifDe,
  navAcheteur,
  navOrganisateur,
  useCompteARebours,
  useEnLigne,
  type LienNav,
  type Motif,
} from './outils';
export { Indicateur, Indicateurs, Pastille, Tableau, type ColonneTableau, type TonPastille } from './Pilotage';
export { Avis, Chargement, EtatVide } from './Retour';
