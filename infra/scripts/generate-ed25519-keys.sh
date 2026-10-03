#!/usr/bin/env bash
# Génère une paire de clés Ed25519 pour le développement local.
# La clé privée signe les QR codes côté backend ; la clé publique est
# distribuée à la PWA scanner pour vérification hors ligne (voir ADR 0004).
#
# NE JAMAIS utiliser les clés générées par ce script en production —
# générer une paire dédiée et l'injecter via un secret manager.

set -euo pipefail

BACKEND_KEYS_DIR="$(dirname "$0")/../../backend/src/main/resources/keys"
WEB_KEYS_DIR="$(dirname "$0")/../../web/public/keys"

mkdir -p "$BACKEND_KEYS_DIR" "$WEB_KEYS_DIR"

PRIVATE_KEY="$BACKEND_KEYS_DIR/ed25519_private_dev.pem"
PUBLIC_KEY="$BACKEND_KEYS_DIR/ed25519_public.pem"

if [ -f "$PRIVATE_KEY" ]; then
  echo "Une clé privée de dev existe déjà ($PRIVATE_KEY) — rien à faire."
  echo "Supprime-la manuellement si tu veux régénérer une nouvelle paire."
  exit 0
fi

echo "Génération de la paire de clés Ed25519 (dev uniquement)..."
openssl genpkey -algorithm ed25519 -out "$PRIVATE_KEY"
openssl pkey -in "$PRIVATE_KEY" -pubout -out "$PUBLIC_KEY"

# Même clé publique partagée côté PWA, pour que le scanner puisse vérifier
# les QR signés par ce backend local.
cp "$PUBLIC_KEY" "$WEB_KEYS_DIR/ed25519_public.pem"

echo "Clé privée (backend uniquement, ignorée par git) : $PRIVATE_KEY"
echo "Clé publique (partagée backend + web)            : $PUBLIC_KEY"
