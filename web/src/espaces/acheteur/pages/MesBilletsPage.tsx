import { useEffect, useState } from 'react';
import { BoutonLien, Cadre, CarteEvenement, Chargement, EtatVide, GrilleEvenements } from '@/ui';
import { listerBilletsRecus, listerCommandesLocales, type BilletRecuLocal, type CommandeLocale } from '../commandesLocales';
import s from './pages.module.css';

/**
 * Les commandes et les billets reçus par transfert de CET appareil : sans compte, il n'y
 * a rien à demander au serveur.
 */
export function MesBilletsPage() {
  const [commandes, setCommandes] = useState<CommandeLocale[] | null>(null);
  const [recus, setRecus] = useState<BilletRecuLocal[]>([]);

  useEffect(() => {
    listerCommandesLocales()
      .then(setCommandes)
      .catch(() => setCommandes([]));
    listerBilletsRecus()
      .then(setRecus)
      .catch(() => setRecus([]));
  }, []);

  const maintenant = Date.now();
  const aVenir = commandes?.filter((c) => new Date(c.debuteLe).getTime() >= maintenant - 12 * 3600_000) ?? [];
  const passees = commandes?.filter((c) => !aVenir.includes(c)) ?? [];

  return (
    <Cadre>
      <div className={s.pile}>
        <div>
          <h1 className={s.titrePage}>Mes billets</h1>
          <p className={s.sousTitre}>Enregistrés sur cet appareil</p>
        </div>

        {commandes === null && <Chargement />}

        {commandes?.length === 0 && recus.length === 0 && (
          <EtatVide
            titre="Aucun billet pour l'instant"
            texte="Les billets achetés depuis ce téléphone ou ce navigateur apparaîtront ici, même sans connexion."
            action={<BoutonLien to="/">Découvrir les événements</BoutonLien>}
          />
        )}

        {aVenir.length > 0 && (
          <GrilleEvenements libelle="Événements à venir">
            {aVenir.map((c) => (
              <CarteEvenement
                key={c.id}
                evenement={{ id: c.eventId, titre: c.titre, debuteLe: c.debuteLe, lieu: c.lieu }}
                vers={`/commandes/${c.id}`}
              />
            ))}
          </GrilleEvenements>
        )}

        {recus.length > 0 && (
          <section className={s.pile} aria-labelledby="titre-recus">
            <h2 id="titre-recus" className={s.titreSection}>
              Reçus par transfert
            </h2>
            <GrilleEvenements libelle="Billets reçus par transfert">
              {recus.map((r) => (
                <CarteEvenement
                  key={r.id}
                  evenement={{ id: r.billet.eventId, titre: r.titre, debuteLe: r.debuteLe, lieu: r.lieu }}
                  vers={`/billets/recu/${r.id}`}
                />
              ))}
            </GrilleEvenements>
          </section>
        )}

        {passees.length > 0 && (
          <section className={s.pile} aria-labelledby="titre-passes">
            <h2 id="titre-passes" className={s.titreSection}>
              Passés
            </h2>
            <GrilleEvenements libelle="Événements passés">
              {passees.map((c) => (
                <CarteEvenement
                  key={c.id}
                  evenement={{ id: c.eventId, titre: c.titre, debuteLe: c.debuteLe, lieu: c.lieu }}
                  vers={`/commandes/${c.id}`}
                />
              ))}
            </GrilleEvenements>
          </section>
        )}
      </div>
    </Cadre>
  );
}
