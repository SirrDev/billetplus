import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { formatDateCourte, formatFcfa } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampTexte, Chargement, EtatVide, Panneau, Pastille, Tableau, type ColonneTableau } from '@/ui';
import { enregistrerReversement, suspendreEvenement, lireReversements, titreEvenement, type LigneReversement } from '../api';
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
      titre: 'Virement',
      cellule: (l) =>
        !l.numeroReversement ? (
          <Pastille ton="danger">Organisateur non vérifié</Pastille>
        ) : l.reversableLe && l.reversableLe > aujourdhui() ? (
          <Pastille ton="attention">À partir du {formatDateCourte(l.reversableLe)}</Pastille>
        ) : l.billetsScannes === 0 ? (
          <Pastille ton="attention">Aucun billet scanné</Pastille>
        ) : (
          <span>
            {l.numeroReversement}
            <br />
            {l.titulaireReversement}
          </span>
        ),
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
  const [derogation, setDerogation] = useState('');
  const [motifSuspension, setMotifSuspension] = useState('');
  const [erreurs, setErreurs] = useState<{ montant?: string; motif?: string; derogation?: string }>({});
  const sansScan = ligne.billetsScannes === 0;
  /** Montant au-delà du reste à reverser : enregistré seulement après une seconde validation. */
  const [confirmeDepassement, setConfirmeDepassement] = useState(false);
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setErreur(null);
    const valeur = entierSigne(montant);
    const nouvelles: { montant?: string; motif?: string; derogation?: string } = {};
    if (valeur === null || valeur === 0) nouvelles.montant = 'Montant entier en FCFA, différent de 0.';
    if (valeur !== null && valeur < 0 && !motif.trim()) nouvelles.motif = 'Une correction (montant négatif) doit être motivée.';
    if (valeur !== null && valeur > 0 && sansScan && !derogation.trim()) {
      nouvelles.derogation = 'Aucun billet scanné : motivez la dérogation (entrée sans contrôle), ou ne virez pas.';
    }
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0 || valeur === null) return;
    if (valeur > Math.max(reste, 0) && !confirmeDepassement) {
      setConfirmeDepassement(true);
      return;
    }
    setEnvoi(true);
    try {
      await enregistrerReversement({
        eventId: ligne.eventId,
        montantFcfa: valeur,
        reference: reference.trim(),
        motif: motif.trim() || null,
        reverseLe: date,
        derogationScans: sansScan && valeur > 0 ? derogation.trim() : null,
      });
      onEnregistre(`Virement de ${formatFcfa(valeur)} enregistré pour ${titre}.`);
    } catch (e2) {
      setErreur(messageErreur(e2));
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre={`Virement pour ${titre}`} id="virement">
      {ligne.numeroReversement ? (
        <Avis>
          <p>
            Virez uniquement vers le numéro vérifié : <strong>{ligne.numeroReversement}</strong> (
            {ligne.titulaireReversement}).
          </p>
        </Avis>
      ) : (
        <Avis ton="danger">
          <p>Organisateur non vérifié ou suspendu : aucun virement n'est possible, seulement une correction.</p>
        </Avis>
      )}
      <form className={s.formulaire} onSubmit={valider}>
        <ChampTexte
          libelle="Montant viré (FCFA)"
          valeur={montant}
          onChange={(v) => {
            setMontant(v);
            setConfirmeDepassement(false);
          }}
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
        {sansScan && (
          <ChampTexte
            libelle="Dérogation : aucun billet scanné"
            valeur={derogation}
            onChange={setDerogation}
            maxLength={255}
            aide="Seulement pour une entrée sans contrôle : expliquez (10 caractères au moins) pourquoi l'événement a bien eu lieu."
            erreur={erreurs.derogation}
          />
        )}
        {confirmeDepassement && (
          <Avis ton="attention">
            <p>
              Ce montant dépasse le reste à reverser ({formatFcfa(Math.max(reste, 0))}). Vérifiez le virement, puis
              confirmez.
            </p>
          </Avis>
        )}
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        <div className={s.actions}>
          <Bouton type="submit" disabled={envoi}>
            {envoi ? 'Enregistrement…' : confirmeDepassement ? 'Confirmer le virement' : 'Enregistrer le virement'}
          </Bouton>
          <Bouton variante="secondaire" onClick={onFermer} disabled={envoi}>
            Fermer
          </Bouton>
        </div>
      </form>
      {!ligne.evenementAnnule && (
        <div className={s.formulaire}>
          <ChampTexte
            libelle="Motif de la suspension de l'événement"
            valeur={motifSuspension}
            onChange={setMotifSuspension}
            maxLength={500}
            aide="Suspendre : vente fermée, billets annulés, acheteurs à rembourser. Définitif."
          />
          <div>
            <Bouton
              variante="secondaire"
              disabled={envoi || !motifSuspension.trim()}
              onClick={() => {
                setEnvoi(true);
                suspendreEvenement(ligne.eventId, motifSuspension.trim())
                  .then(() => onEnregistre(`Événement ${titre} suspendu.`))
                  .catch((e3: unknown) => {
                    setErreur(messageErreur(e3));
                    setEnvoi(false);
                  });
              }}
            >
              Suspendre l'événement
            </Bouton>
          </div>
        </div>
      )}
    </Panneau>
  );
}
