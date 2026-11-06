import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { formatFcfa } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampTexte, Chargement, EtatVide, Panneau, Pastille, Tableau, type ColonneTableau } from '@/ui';
import { enregistrerReversement, lireReversements, titreEvenement, type LigneReversement } from '../api';
import { CadreAdmin } from './CadreAdmin';
import s from './admin.module.css';

/** Date du jour à Dakar (UTC), au format attendu par le backend. */
const aujourdhui = () => new Date().toISOString().slice(0, 10);

/** Montant saisi : entier, signe moins accepté (correction), espaces ignorés. */
function entierSigne(valeur: string): number | null {
  const texte = valeur.replace(/\s/g, '');
  return /^-?\d+$/.test(texte) ? Number(texte) : null;
}

/**
 * Reversements aux organisateurs (ADR 0009) : pour chaque événement qui a des ventes, la
 * part de l'organisateur (commission de Billet+ déduite), ce qui a déjà été reversé, et ce
 * qu'il reste à reverser. Billet+ ne fait aucun virement : l'administrateur paie lui-même
 * (Wave, Orange Money), puis enregistre ici le virement.
 */
export function ReversementsPage() {
  const [lignes, setLignes] = useState<LigneReversement[] | null>(null);
  const [titres, setTitres] = useState<Record<string, string | null>>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [choisi, setChoisi] = useState<LigneReversement | null>(null);
  const [retour, setRetour] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setErreur(null);
    try {
      const liste = await lireReversements();
      setLignes(liste);
      const recus = await Promise.all(liste.map(async (l) => [l.eventId, await titreEvenement(l.eventId)] as const));
      setTitres(Object.fromEntries(recus));
    } catch (e) {
      setErreur(messageErreur(e));
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  const titre = (l: LigneReversement) => titres[l.eventId] ?? 'Événement non visible';

  const colonnes: ReadonlyArray<ColonneTableau<LigneReversement>> = [
    {
      titre: 'Événement',
      cellule: (l) => (
        <span>
          <strong>{titre(l)}</strong>
          {l.evenementAnnule && (
            <>
              {' '}
              <Pastille ton="danger">Annulé</Pastille>
            </>
          )}
        </span>
      ),
    },
    { titre: 'Ventes', cellule: (l) => formatFcfa(l.recettes.brutFcfa), nombre: true },
    { titre: 'Commission', cellule: (l) => formatFcfa(l.recettes.commissionFcfa), nombre: true },
    { titre: "Part de l'organisateur", cellule: (l) => formatFcfa(l.recettes.netFcfa), nombre: true },
    { titre: 'Déjà reversé', cellule: (l) => formatFcfa(l.recettes.reverseFcfa), nombre: true },
    {
      titre: 'Reste à reverser',
      cellule: (l) =>
        l.recettes.resteAReverserFcfa < 0 ? (
          <Pastille ton="danger">Trop reversé : {formatFcfa(-l.recettes.resteAReverserFcfa)}</Pastille>
        ) : (
          <strong>{formatFcfa(l.recettes.resteAReverserFcfa)}</strong>
        ),
      nombre: true,
    },
    {
      titre: 'Action',
      cellule: (l) => (
        <Bouton
          variante="secondaire"
          aria-label={`Enregistrer un virement pour ${titre(l)}`}
          onClick={() => {
            setRetour(null);
            setChoisi(l);
          }}
        >
          Enregistrer un virement
        </Bouton>
      ),
    },
  ];

  return (
    <CadreAdmin>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Administration</p>
          <h1 className={s.titrePage}>Reversements aux organisateurs</h1>
        </div>
        <Avis>
          <p>
            La part de l'organisateur est le montant des ventes moins la commission de Billet+, calculée commande par
            commande. Faites le virement vous-même (Wave, Orange Money), puis enregistrez-le ici avec sa référence.
          </p>
        </Avis>
        {retour && (
          <Avis ton="succes">
            <p>{retour}</p>
          </Avis>
        )}
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
            <div>
              <Bouton variante="secondaire" onClick={() => void charger()}>
                Réessayer
              </Bouton>
            </div>
          </Avis>
        )}
        {!erreur && lignes === null && <Chargement />}
        {lignes?.length === 0 && (
          <EtatVide titre="Aucune vente à reverser" texte="Les événements apparaîtront ici dès leur première vente payée." />
        )}
        {choisi && (
          <FormulaireVirement
            key={choisi.eventId}
            ligne={choisi}
            titre={titre(choisi)}
            onFermer={() => setChoisi(null)}
            onEnregistre={(texte) => {
              setChoisi(null);
              setRetour(texte);
              void charger();
            }}
          />
        )}
        {lignes && lignes.length > 0 && (
          <Panneau>
            <Tableau legende="Reversements par événement" colonnes={colonnes} lignes={lignes} cle={(l) => l.eventId} />
          </Panneau>
        )}
      </div>
    </CadreAdmin>
  );
}

function FormulaireVirement({
  ligne,
  titre,
  onFermer,
  onEnregistre,
}: {
  ligne: LigneReversement;
  titre: string;
  onFermer: () => void;
  onEnregistre: (texte: string) => void;
}) {
  const reste = ligne.recettes.resteAReverserFcfa;
  const [montant, setMontant] = useState(reste > 0 ? String(reste) : '');
  const [reference, setReference] = useState('');
  const [date, setDate] = useState(aujourdhui());
  const [motif, setMotif] = useState('');
  const [erreurs, setErreurs] = useState<{ montant?: string; motif?: string }>({});
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const valeur = entierSigne(montant);
    const nouvelles: { montant?: string; motif?: string } = {};
    if (valeur === null || valeur === 0) nouvelles.montant = 'Montant entier en FCFA, différent de 0.';
    if (valeur !== null && valeur < 0 && !motif.trim()) nouvelles.motif = 'Une correction (montant négatif) doit être motivée.';
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0 || valeur === null) return;
    setEnvoi(true);
    try {
      await enregistrerReversement({
        eventId: ligne.eventId,
        montantFcfa: valeur,
        reference: reference.trim(),
        motif: motif.trim() || null,
        reverseLe: date,
      });
      onEnregistre(`Virement de ${formatFcfa(valeur)} enregistré pour ${titre}.`);
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre={`Virement pour ${titre}`} id="virement">
      <form className={s.formulaire} onSubmit={valider}>
        <ChampTexte
          libelle="Montant viré (FCFA)"
          valeur={montant}
          onChange={setMontant}
          requis
          inputMode="numeric"
          aide={`Reste à reverser : ${formatFcfa(reste)}. Un montant négatif corrige une erreur.`}
          erreur={erreurs.montant}
        />
        <ChampTexte
          libelle="Référence du virement"
          valeur={reference}
          onChange={setReference}
          requis
          maxLength={100}
          aide="Identifiant de la transaction Wave ou Orange Money."
        />
        <ChampTexte libelle="Date du virement" type="date" valeur={date} onChange={setDate} requis />
        <ChampTexte
          libelle="Motif"
          valeur={motif}
          onChange={setMotif}
          maxLength={255}
          aide="Obligatoire pour une correction."
          erreur={erreurs.motif}
        />
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        <div className={s.actions}>
          <Bouton type="submit" disabled={envoi}>
            {envoi ? 'Enregistrement…' : 'Enregistrer le virement'}
          </Bouton>
          <Bouton variante="secondaire" onClick={onFermer} disabled={envoi}>
            Fermer
          </Bouton>
        </div>
      </form>
    </Panneau>
  );
}
