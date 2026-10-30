import { useEffect, useState } from 'react';
import { BoutonLien, Cadre, CarteEvenement, Chargement, EtatVide, GrilleEvenements } from '@/ui';
import { listerCommandesLocales, type CommandeLocale } from '../commandesLocales';
import s from './pages.module.css';

/** Les commandes de CET appareil : sans compte, il n'y a rien à demander au serveur. */
export function MesBilletsPage() {
  const [commandes, setCommandes] = useState<CommandeLocale[] | null>(null);

  useEffect(() => {
    listerCommandesLocales()
      .then(setCommandes)
      .catch(() => setCommandes([]));
  }, []);

  const maintenant = Date.now();
  const aVenir = commandes?.filter((c) => new Date(c.debuteLe).getTime() >= maintenant - 12 * 3600_000) ?? [];
  const passees = commandes?.filter((c) => !aVenir.includes(c)) ?? [];

  return (
    <Cadre>
      <div className={s.pile}>
        <div>
          <p className={s.surtitre}>Enregistrés sur cet appareil</p>
          <h1 className={s.titrePage}>Mes billets</h1>
        </div>

        {commandes === null && <Chargement />}

        {commandes?.length === 0 && (
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
