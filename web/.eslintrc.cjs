module.exports = {
  root: true,
  env: { browser: true, es2021: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', 'dev-dist', '.eslintrc.cjs', 'vite.config.ts'],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    ecmaFeatures: { jsx: true },
  },
  plugins: ['react-refresh'],
  rules: {
    'react-refresh/only-export-components': [
      'warn',
      { allowConstantExport: true },
    ],
    'no-restricted-imports': [
      'error',
      {
        patterns: [
          {
            group: ['**/routes/organisateur/*', '!**/routes/organisateur/index*'],
            message:
              "N'importez pas directement depuis routes/organisateur/ en dehors de ce dossier. Passez par src/shared/ si le code doit être partagé.",
          },
          {
            group: ['**/routes/acheteur/*', '!**/routes/acheteur/index*'],
            message:
              "N'importez pas directement depuis routes/acheteur/ en dehors de ce dossier. Passez par src/shared/ si le code doit être partagé.",
          },
          {
            group: ['**/routes/scanner/*', '!**/routes/scanner/index*'],
            message:
              "N'importez pas directement depuis routes/scanner/ en dehors de ce dossier. Passez par src/shared/ si le code doit être partagé.",
          },
        ],
      },
    ],
  },
};