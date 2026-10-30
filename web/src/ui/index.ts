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
export { Bouton, BoutonLien } from './Bouton';
export { Cadre } from './Cadre';
export { CarteEvenement, EvenementALaUne, GrilleEvenements, type ResumeEvenement } from './CarteEvenement';
export { ChampRecherche, ChampTelephone, ChoixCartes, type OptionCarte } from './Formulaire';
export { Icone, type NomIcone } from './Icone';
export { codeBillet, motifDe, navAcheteur, useCompteARebours, type LienNav, type Motif } from './outils';
export { Avis, Chargement, EtatVide } from './Retour';
