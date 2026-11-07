import { useCallback, useEffect, useId, useState, type ChangeEvent, type FormEvent } from 'react';
import { estMobileSenegalais, formatDateCourte, versE164 } from '@/commun/format';
import { messageErreur } from '@/commun/http';
import {
  Avis,
  Bouton,
  ChampTelephone,
  ChampTexte,
  Chargement,
  ChoixCartes,
  Panneau,
  Pastille,
  type OptionCarte,
  type TonPastille,
} from '@/ui';
import {
  declarerVerification,
  deposerPiece,
  lireVerification,
  soumettreVerification,
  type StatutVerification,
  type TypePiece,
  type Verification,
} from '../api';
import { CadreOrganisateur } from './CadreOrganisateur';
import s from './organisateur.module.css';

type Titulaire = 'PERSONNE' | 'STRUCTURE';

const TITULAIRES: ReadonlyArray<OptionCarte<Titulaire>> = [
  { valeur: 'PERSONNE', titre: 'Une personne', detail: 'Vous organisez en votre nom' },
  { valeur: 'STRUCTURE', titre: 'Une structure', detail: 'Association ou entreprise (NINEA ou RCCM)' },
];

const STATUTS: Record<StatutVerification, { libelle: string; ton: TonPastille; texte: string }> = {
  A_VERIFIER: {
    libelle: 'À vérifier',
    ton: 'attention',
    texte: 'Renseignez le titulaire, déposez vos pièces, puis demandez la vérification.',
  },
  EN_REVUE: {
    libelle: 'En cours de vérification',
    ton: 'info',
    texte: "L'équipe Billet+ examine vos pièces. Vous pourrez publier dès qu'elles seront acceptées.",
  },
  VERIFIE: {
    libelle: 'Vérifié',
    ton: 'succes',
    texte: 'Vous pouvez publier vos événements. Changer de numéro ou de pièce relance la vérification.',
  },
  REFUSE: {
    libelle: 'Refusé',
    ton: 'danger',
    texte: 'Corrigez ce qui est indiqué, puis demandez une nouvelle vérification.',
  },
  SUSPENDU: {
    libelle: 'Suspendu',
    ton: 'danger',
    texte: "Ce compte est suspendu par Billet+. Contactez l'équipe Billet+.",
  },
};

const PIECES: Record<TypePiece, { titre: string; detail: string }> = {
  IDENTITE: { titre: "Pièce d'identité", detail: 'CNI ou passeport du responsable, lisible.' },
  JUSTIFICATIF_REVERSEMENT: {
    titre: 'Justificatif du numéro de reversement',
    detail: 'Capture du compte Wave ou Orange Money montrant le nom du titulaire.',
  },
  NINEA_RCCM: { titre: 'NINEA ou registre du commerce', detail: 'Document officiel de la structure.' },
};

/**
 * Vérification du compte organisateur (ADR 0012) : sans elle, aucun événement ne se publie et
 * aucun reversement n'est possible. Les pièces sont chiffrées par Billet+ et lues seulement par
 * son équipe ; elles sont supprimées 5 ans après le dernier reversement.
 */
export function VerificationPage() {
  const [verification, setVerification] = useState<Verification | null>(null);
  const [erreur, setErreur] = useState<string | null>(null);

  const charger = useCallback(async () => {
    setErreur(null);
    try {
      setVerification(await lireVerification());
    } catch (e) {
      setErreur(messageErreur(e));
    }
  }, []);

  useEffect(() => {
    void charger();
  }, [charger]);

  return (
    <CadreOrganisateur etroit>
      <div className={s.pile}>
        <div>
          <h1 className={s.titrePage}>Vérification du compte</h1>
          <p className={s.sousTitre}>Espace organisateur</p>
        </div>
        {erreur && (
          <Avis ton="danger">
            <p>{erreur}</p>
          </Avis>
        )}
        {!verification && !erreur && <Chargement />}
        {verification && <Contenu verification={verification} onChange={setVerification} />}
      </div>
    </CadreOrganisateur>
  );
}

function Contenu({ verification, onChange }: { verification: Verification; onChange: (v: Verification) => void }) {
  const statut = STATUTS[verification.statut];
  // Pendant l'examen, le dossier est figé : l'équipe décide sur ce qu'elle a vu (ADR 0012).
  const modifiable = verification.statut !== 'SUSPENDU' && verification.statut !== 'EN_REVUE';
  const deposees = new Set(verification.pieces.map((p) => p.type));
  const complet =
    verification.typeTitulaire !== null && verification.piecesExigees.every((t) => deposees.has(t));
  const [message, setMessage] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const soumettre = async () => {
    setMessage(null);
    setEnvoi(true);
    try {
      onChange(await soumettreVerification());
      setMessage({ ton: 'succes', texte: 'Demande envoyée : l’équipe Billet+ va examiner vos pièces.' });
    } catch (e) {
      setMessage({ ton: 'danger', texte: messageErreur(e) });
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <>
      <Panneau titre="État" id="etat">
        <div>
          <Pastille ton={statut.ton}>{statut.libelle}</Pastille>
        </div>
        <p className={s.ligneDetail}>{statut.texte}</p>
        {verification.motif && (
          <Avis ton="danger">
            <p>
              <strong>Motif :</strong> {verification.motif}
            </p>
          </Avis>
        )}
        <p className={s.ligneDetail}>
          Billet+ reverse la part de vos ventes après l'événement (à partir du lendemain, si des billets ont été
          scannés à l'entrée), uniquement vers votre numéro vérifié.
        </p>
        {verification.numeroReversement && (
          <p className={s.ligneDetail}>
            Numéro de reversement : <strong>{verification.numeroReversement}</strong> ({verification.titulaireReversement})
          </p>
        )}
      </Panneau>

      {modifiable && <Declaration verification={verification} onChange={onChange} />}

      {modifiable && verification.typeTitulaire && (
        <Panneau titre="Pièces" id="pieces">
          <p className={s.ligneDetail}>
            PDF, JPEG ou PNG, 5 Mo au plus. Vos pièces sont chiffrées et lues seulement par l'équipe Billet+.
          </p>
          {verification.piecesExigees.map((type) => (
            <Piece key={type} type={type} verification={verification} onChange={onChange} />
          ))}
        </Panneau>
      )}

      {message && (
        <Avis ton={message.ton}>
          <p>{message.texte}</p>
        </Avis>
      )}
      {(verification.statut === 'A_VERIFIER' || verification.statut === 'REFUSE') && (
        <Bouton onClick={() => void soumettre()} disabled={!complet || envoi}>
          {envoi ? 'Envoi…' : 'Demander la vérification'}
        </Bouton>
      )}
    </>
  );
}

function Declaration({ verification, onChange }: { verification: Verification; onChange: (v: Verification) => void }) {
  const [type, setType] = useState<Titulaire>(verification.typeTitulaire ?? 'PERSONNE');
  const [nomStructure, setNomStructure] = useState(verification.nomStructure ?? '');
  const [ninea, setNinea] = useState(verification.ninea ?? '');
  const [numero, setNumero] = useState(verification.numeroReversement?.replace('+221', '') ?? '');
  const [titulaire, setTitulaire] = useState(verification.titulaireReversement ?? '');
  const [erreurs, setErreurs] = useState<{ numero?: string; titulaire?: string; structure?: string }>({});
  const [message, setMessage] = useState<{ ton: 'succes' | 'danger'; texte: string } | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const valider = async (e: FormEvent) => {
    e.preventDefault();
    setMessage(null);
    const nouvelles: typeof erreurs = {};
    if (!estMobileSenegalais(numero)) nouvelles.numero = 'Mobile sénégalais à 9 chiffres, par exemple 77 123 45 67.';
    if (!titulaire.trim()) nouvelles.titulaire = 'Nom du titulaire du numéro, tel qu’il apparaît sur le compte.';
    if (type === 'STRUCTURE' && (!nomStructure.trim() || !ninea.trim())) {
      nouvelles.structure = 'Nom de la structure et NINEA (ou RCCM) obligatoires.';
    }
    setErreurs(nouvelles);
    if (Object.keys(nouvelles).length > 0) return;
    setEnvoi(true);
    try {
      onChange(
        await declarerVerification({
          typeTitulaire: type,
          nomStructure: type === 'STRUCTURE' ? nomStructure.trim() : null,
          ninea: type === 'STRUCTURE' ? ninea.trim() : null,
          numeroReversement: versE164(numero),
          titulaireReversement: titulaire.trim(),
        }),
      );
      setMessage({ ton: 'succes', texte: 'Informations enregistrées.' });
    } catch (e2) {
      setMessage({ ton: 'danger', texte: messageErreur(e2) });
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <Panneau titre="Titulaire et numéro de reversement" id="titulaire">
      <form className={s.formulaire} onSubmit={valider} noValidate>
        <ChoixCartes legende="Vous organisez en tant que" nom="titulaire" options={TITULAIRES} valeur={type} onChange={setType} />
        {type === 'STRUCTURE' && (
          <>
            <ChampTexte libelle="Nom de la structure" valeur={nomStructure} onChange={setNomStructure} maxLength={255} />
            <ChampTexte libelle="NINEA ou RCCM" valeur={ninea} onChange={setNinea} maxLength={30} erreur={erreurs.structure} />
          </>
        )}
        <ChampTelephone
          libelle="Numéro de reversement"
          valeur={numero}
          onChange={setNumero}
          aide="Numéro Wave ou Orange Money qui recevra vos reversements."
          erreur={erreurs.numero}
        />
        <ChampTexte
          libelle="Titulaire du numéro"
          valeur={titulaire}
          onChange={setTitulaire}
          maxLength={255}
          aide="Doit correspondre à la pièce d'identité ou à la structure."
          erreur={erreurs.titulaire}
        />
        {verification.statut === 'VERIFIE' && (
          <Avis ton="attention">
            <p>Modifier ces informations relance la vérification : aucun reversement d'ici là.</p>
          </Avis>
        )}
        {message && (
          <Avis ton={message.ton}>
            <p>{message.texte}</p>
          </Avis>
        )}
        <Bouton type="submit" variante="secondaire" disabled={envoi}>
          {envoi ? 'Enregistrement…' : 'Enregistrer'}
        </Bouton>
      </form>
    </Panneau>
  );
}

function Piece({
  type,
  verification,
  onChange,
}: {
  type: TypePiece;
  verification: Verification;
  onChange: (v: Verification) => void;
}) {
  const id = useId();
  const deposee = verification.pieces.find((p) => p.type === type);
  const [erreur, setErreur] = useState<string | null>(null);
  const [envoi, setEnvoi] = useState(false);

  const choisir = async (e: ChangeEvent<HTMLInputElement>) => {
    const fichier = e.target.files?.[0];
    e.target.value = '';
    if (!fichier) return;
    setErreur(null);
    if (fichier.size > 5 * 1024 * 1024) {
      setErreur('Le fichier doit faire 5 Mo au plus.');
      return;
    }
    setEnvoi(true);
    try {
      onChange(await deposerPiece(type, fichier));
    } catch (e2) {
      setErreur(messageErreur(e2));
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div className={s.formulaire}>
      <p>
        <strong>{PIECES[type].titre}</strong>{' '}
        {deposee ? (
          <Pastille ton="succes">Déposée le {formatDateCourte(deposee.deposeLe)}</Pastille>
        ) : (
          <Pastille ton="attention">À déposer</Pastille>
        )}
      </p>
      <p className={s.ligneDetail}>{PIECES[type].detail}</p>
      <label htmlFor={id} className={s.ligneDetail}>
        {deposee ? 'Remplacer le fichier' : 'Choisir le fichier'}
      </label>
      <input
        id={id}
        type="file"
        accept="application/pdf,image/jpeg,image/png"
        onChange={(e) => void choisir(e)}
        disabled={envoi}
        aria-label={`${PIECES[type].titre} : choisir le fichier`}
      />
      {envoi && <p className={s.ligneDetail}>Envoi…</p>}
      {erreur && (
        <Avis ton="danger">
          <p>{erreur}</p>
        </Avis>
      )}
    </div>
  );
}
