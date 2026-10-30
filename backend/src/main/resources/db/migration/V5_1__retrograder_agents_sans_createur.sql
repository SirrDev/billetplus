-- Reprise de données AVANT V6 (Flyway l'exécute entre V5 et V6, d'où la version 5.1).
--
-- V6 impose qu'un compte AGENT ait un créateur (chk_users_agent_a_un_createur), en
-- supposant qu'aucun AGENT n'existait avant elle. C'est faux sur les bases où l'ancien
-- POST /api/auth/register laissait le client choisir son rôle (corrigé le 20/10/2026,
-- fix/auth-register-role-restriction) : ces comptes AGENT n'ont pas de créateur, et V6
-- échouait, empêchant l'application de démarrer.
--
-- Ces comptes se sont attribué leur rôle eux-mêmes. Ils redeviennent ORGANISATEUR,
-- le rôle que crée aujourd'hui l'inscription publique : aucune donnée n'est perdue,
-- et aucun privilège n'est gagné au-delà de ce que l'inscription donne à tout le monde.
-- Sur une base créée après le correctif (CI, tests, production), aucune ligne n'est touchée.

UPDATE users SET role = 'ORGANISATEUR' WHERE role = 'AGENT';
