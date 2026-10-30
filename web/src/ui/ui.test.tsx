import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Cadre, ChampTelephone, ChoixCartes, codeBillet, motifDe, PasseBillet, SelecteurQuantite } from '@/ui';

describe('SelecteurQuantite', () => {
  function Selecteur({ max = 2 }: { max?: number }) {
    const [n, setN] = useState(0);
    return <SelecteurQuantite nom="VIP" valeur={n} max={max} onChange={setN} />;
  }

  it('a des boutons nommés et respecte les bornes', async () => {
    render(<Selecteur />);
    const moins = screen.getByRole('button', { name: 'Retirer un billet VIP' });
    const plus = screen.getByRole('button', { name: 'Ajouter un billet VIP' });
    expect(screen.getByRole('group', { name: 'Quantité VIP' })).toBeInTheDocument();
    expect(moins).toBeDisabled();

    await userEvent.click(plus);
    await userEvent.click(plus);
    expect(screen.getByRole('status')).toHaveTextContent('2');
    expect(plus).toBeDisabled();
    expect(moins).toBeEnabled();
  });
});

describe('ChampTelephone', () => {
  function Champ({ erreur }: { erreur?: string }) {
    const [v, setV] = useState('');
    return <ChampTelephone valeur={v} onChange={setV} aide="Identifie la commande." erreur={erreur} />;
  }

  it('met le numéro en forme et relie aide et erreur au champ', async () => {
    render(<Champ erreur="Numéro invalide" />);
    const champ = screen.getByLabelText('Numéro de mobile');
    await userEvent.type(champ, '771234567');
    expect(champ).toHaveValue('77 123 45 67');
    expect(champ).toHaveAccessibleDescription('Identifie la commande. Numéro invalide');
    expect(champ).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Numéro invalide');
  });
});

describe('ChoixCartes', () => {
  it('utilise de vrais boutons radio dans un groupe nommé', async () => {
    const choix = vi.fn();
    render(
      <ChoixCartes
        legende="Moyen de paiement"
        nom="m"
        valeur="A"
        onChange={choix}
        options={[
          { valeur: 'A', titre: 'Wave' },
          { valeur: 'B', titre: 'Orange Money' },
        ]}
      />,
    );
    expect(screen.getByRole('group', { name: 'Moyen de paiement' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Wave' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'Orange Money' }));
    expect(choix).toHaveBeenCalledWith('B');
  });
});

describe('PasseBillet', () => {
  const commun = { id: '4f2a91c0-0000-0000-0000-000000000000', categorie: 'Standard', titre: 'Nuit', sousTitre: 'sam.' };

  it('dessine le QR signé fourni par le backend pour un billet valide', () => {
    render(<PasseBillet {...commun} qr="charge.signature" statut="EMIS" />);
    expect(screen.getByRole('img', { name: 'QR code du billet BP-4F2A-91C0' })).toBeInTheDocument();
    expect(screen.getByText('Valide')).toBeInTheDocument();
  });

  it("n'affiche aucun QR pour un billet déjà utilisé", () => {
    render(<PasseBillet {...commun} qr={null} statut="SCANNE" />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(screen.getByText('Déjà utilisé')).toBeInTheDocument();
  });

  it('dérive un code de secours lisible et un motif stable', () => {
    expect(codeBillet(commun.id)).toBe('BP-4F2A-91C0');
    expect(motifDe('abc')).toBe(motifDe('abc'));
  });
});

describe('Cadre', () => {
  it('marque la page courante dans la navigation', () => {
    render(
      <MemoryRouter initialEntries={['/billets']}>
        <Cadre>contenu</Cadre>
      </MemoryRouter>,
    );
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mes billets' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Découvrir' })).not.toHaveAttribute('aria-current');
  });

  it('masque la navigation sur les écrans de parcours', () => {
    render(
      <MemoryRouter>
        <Cadre sansNav>contenu</Cadre>
      </MemoryRouter>,
    );
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });
});
