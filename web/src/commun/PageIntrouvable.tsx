import { useLocation } from 'react-router-dom';
import { BoutonLien, Cadre, EtatVide, navAcheteur, navOrganisateur } from '@/ui';

/**
 * Adresse inconnue : un vrai message (et non une redirection silencieuse vers l'accueil),
 * avec un retour adapté à l'espace où l'on se trouvait.
 */
export function PageIntrouvable() {
  const { pathname } = useLocation();
  const organisateur = pathname.startsWith('/organisateur');
  const controle = pathname.startsWith('/scanner');
  const admin = pathname.startsWith('/admin');
  const retour = organisateur ? '/organisateur' : controle ? '/scanner' : admin ? '/admin' : '/';
  const libelle = organisateur ? 'Mes événements' : controle ? 'Contrôle d’entrée' : admin ? 'Remboursements' : 'Découvrir les événements';
  return (
    <Cadre liens={organisateur ? navOrganisateur : navAcheteur} accueil={retour} sansNav={controle || admin}>
      <EtatVide
        niveau={1}
        icone="recherche"
        titre="Page introuvable"
        texte="Cette adresse n'existe pas ou n'existe plus. Vérifiez le lien, ou repartez de l'accueil."
        action={<BoutonLien to={retour}>{libelle}</BoutonLien>}
      />
    </Cadre>
  );
}
