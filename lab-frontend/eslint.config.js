import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'
import globals from 'globals'

export default [
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'vendor/leibox/**'] },
  js.configs.recommended,
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: {
      'no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', caughtErrors: 'none', varsIgnorePattern: '^_' },
      ],
      // Existing shell index.vue / Sidebar.vue names remain public component names.
      'vue/multi-word-component-names': 'off',
    },
  },
  prettier,
]
