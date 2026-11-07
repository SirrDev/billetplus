import { useCallback, useEffect, useState } from 'react';
import { formatDateCourte } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import { Avis, Bouton, ChampChoix, ChampTexte, Chargement, EtatVide, Panneau, Pastille } from '@/ui';
import {
  accepterVerification,
  lirePiece,
  lireVerifications,
  refuserVerification,
  suspendreCompte,
  type StatutVerification,
  type TypePiece,
  type VerificationOrganisateur,
} from '../api';
import { CadreAdmin } from './CadreAdmin';
import s from './admin.module.css';

const STATUTS: ReadonlyArray<{ valeur: StatutVerification; libelle: string }> = [
  { valeur: 'EN_REVUE', libelle: 'À examiner' },
  { valeur: 'VERIFIE', libelle: 'Vérifiés' },
  { valeur: 'REFUSE', libelle: 'Refusés' },
  { valeur: 'SUSPENDU', libelle: 'Suspendus' },
];

function extension(typeMime: string): string {
  return typeMime === 'application/pdf' ? '.pdf' : typeMime === 'image/png' ? '.png' : '.jpg';
}

const PIECES: Record<TypePiece, string> = {
  IDENTITE: "Pièce d'identité",
  JUSTIFICATIF_REVERSEMENT: 'Justificatif du numéro',
  NINEA_RCCM: 'NINEA ou RCCM',
};

/**
 * Vérification des organisateurs (ADR 0012) : l'administrateur ouvre les pièces (déchiffrées
 * par le backend, jamais gardées par la PWA), compare le titulaire du numéro à la pièce
 * d'identité, puis accepte, refuse (motif) ou suspend (motif, définitif).
 */
export function VerificationsPage() {
  const [statut, setStatut] = useState<StatutVerification>('EN_REVUE');
  const [liste, setListe] = useState<VerificationOrganisateur[] | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);
  const [retour, setRetour] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setErreur(null);
    setListe(null);
    try {
      setListe(await lireVerifications(statut));
    } catch (e) {
      setErreur(messageErreur(e));
    }
  }, [statut]);

  useEffect(() => {
    void charger();
  }, [charger]);

  return (
    <CadreAdmin>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Administration</p>
          <h1 className={s.titrePage}>Vérification des organisateurs</h1>
        </div>
        <Avis>
          <p>
            Téléchargez chaque pièce, vérifiez que le titulaire du numéro de reversement correspond à la pièce d'identité (ou
            à la structure), puis décidez. Un organisateur non vérifié ne peut ni publier ni recevoir de reversement.
          </p>
        </Avis>
        <ChampChoix libelle="Afficher" options={STATUTS} valeur={statut} onChange={setStatut} />
        {retour && (
          <Avis ton="succes">
            <p>{retour}</p>
          </Avis>
        )}
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        {!erreur && liste === null && <Chargement />}
        {liste?.length === 0 && <EtatVide titre="Aucun organisateur" texte="Rien à afficher pour ce statut." />}
        {liste?.map((v) => (
          <Demande
            key={v.organisateurId}
            verification={v}
            onDecision={(texte) => {
              setRetour(texte);
              void charger();
            }}
          />
        ))}
      </div>
    </CadreAdmin>
  );
}

function Demande({
  verification: v,
  onDecision,
}: {
  verification: VerificationOrganisateur;
  onDecision: (texte: string) => void;
}) {
  const [motif, setMotif] = useState('');
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);
  const nom = v.nomComplet ?? v.email ?? v.organisateurId;

  const agir = async (action: () => Promise<unknown>, texte: string, motifRequis: boolean) => {
    setErreur(null);
    if (motifRequis && !motif.trim()) {
      setErreur('Indiquez le motif : il est montré à l’organisateur.');
      return;
    }
    setEnvoi(true);
    try {
      await action();
      onDecision(texte);
    } catch (e) {
      setErreur(messageErreur(e));
      setEnvoi(false);
    }
  };

  /**
   * Téléchargement, jamais ouverture dans la page : un PDF piégé ne s'affiche pas sous
   * l'origine de Billet+ (relecture de sécurité). L'administrateur l'ouvre avec sa visionneuse.
   */
  const telecharger = async (type: TypePiece) => {
    setErreur(null);
    try {
      const fichier = await lirePiece(v.organisateurId, type);
      const url = URL.createObjectURL(fichier);
      const lien = document.createElement('a');
      lien.href = url;
      lien.download = `${type.toLowerCase()}-${v.organisateurId.slice(0, 8)}${extension(fichier.type)}`;
      document.body.appendChild(lien);
      lien.click();
      lien.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      setErreur(messageErreur(e));
    }
  };

  return (
    <Panneau titre={nom}>
      <dl className={s.fiche}>
        <div>
          <dt>E-mail</dt>
          <dd>{v.email}</dd>
        </div>
        <div>
          <dt>Titulaire</dt>
          <dd>
            {v.typeTitulaire === 'STRUCTURE' ? `${v.nomStructure} (NINEA/RCCM ${v.ninea})` : 'Personne'} ·{' '}
            {v.titulaireReversement}
          </dd>
        </div>
        <div>
          <dt>Numéro de reversement</dt>
          <dd>{v.numeroReversement}</dd>
        </div>
        {v.soumisLe && (
          <div>
            <dt>Demande</dt>
            <dd>{formatDateCourte(v.soumisLe)}</dd>
          </div>
        )}
        {v.motif && (
          <div>
            <dt>Motif</dt>
            <dd>{v.motif}</dd>
          </div>
        )}
      </dl>
      <div className={s.actions}>
        {v.pieces.map((p) => (
          <Bouton key={p.type} variante="secondaire" onClick={() => void telecharger(p.type)}>
            Télécharger : {PIECES[p.type]}
          </Bouton>
        ))}
        {v.piecesExigees
          .filter((t) => !v.pieces.some((p) => p.type === t))
          .map((t) => (
            <Pastille key={t} ton="danger">
              Manque : {PIECES[t]}
            </Pastille>
          ))}
      </div>
      {v.statut !== 'SUSPENDU' && (
        <ChampTexte
          libelle="Motif (refus ou suspension)"
          valeur={motif}
          onChange={setMotif}
          maxLength={500}
          aide="Montré à l'organisateur."
        />
      )}
      {erreur && (
        <Avis ton="danger">
          <p>{erreur}</p>
        </Avis>
      )}
      <div className={s.actions}>
        {v.statut === 'EN_REVUE' && (
          <>
            <Bouton
              onClick={() => void agir(() => accepterVerification(v.organisateurId), `${nom} est vérifié.`, false)}
              disabled={envoi}
            >
              Accepter
            </Bouton>
            <Bouton
              variante="secondaire"
              onClick={() =>
                void agir(() => refuserVerification(v.organisateurId, motif.trim()), `Demande de ${nom} refusée.`, true)
              }
              disabled={envoi}
            >
              Refuser
            </Bouton>
          </>
        )}
        {v.statut !== 'SUSPENDU' && (
          <Bouton
            variante="secondaire"
            onClick={() =>
              void agir(
                () => suspendreCompte(v.organisateurId, motif.trim()),
                `Compte de ${nom} suspendu : ses événements à venir sont annulés.`,
                true,
              )
            }
            disabled={envoi}
          >
            Suspendre le compte
          </Bouton>
        )}
      </div>
    </Panneau>
  );
}
