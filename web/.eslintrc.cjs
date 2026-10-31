/*
 * Chaque espace (acheteur, puis organisateur et contrôle) ne s'importe que par son
 * index.ts : un code partagé va dans src/commun/ ou src/ui/, jamais d'un espace à l'autre.
 */
const isolerEspace = (espace) => ({
    group: [`**/espaces/${espace}/*`, `!**/espaces/${espace}/index*`],
    message: `N'importez pas l'intérieur de espaces/${espace}/ depuis l'extérieur : passez par son index.ts, ou déplacez le code partagé dans src/commun/.`,
});

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
        'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
        'no-restricted-imports': [
            'error',
            { patterns: [isolerEspace('acheteur'), isolerEspace('organisateur'), isolerEspace('controle'), isolerEspace('admin')] },
        ],
    },
    overrides: [
        {
            // Sens des dépendances : les espaces utilisent ui/ et commun/, jamais l'inverse.
            files: ['src/ui/**', 'src/commun/**'],
            excludedFiles: ['**/*.test.ts', '**/*.test.tsx'],
            rules: {
                'no-restricted-imports': [
                    'error',
                    {
                        patterns: [
                            {
                                group: ['@/espaces/*', '**/espaces/*'],
                                message: 'src/ui et src/commun ne dépendent d’aucun espace : déplacez le code partagé ici.',
                            },
                        ],
                    },
                ],
            },
        },
        {
            // Les tests importent librement ce qu'ils testent.
            files: ['**/*.test.ts', '**/*.test.tsx'],
            rules: { 'no-restricted-imports': 'off' },
        },
    ],
};
