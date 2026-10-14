// @ts-check
import withNuxt from './.nuxt/eslint.config.mjs';

export default withNuxt({
  ignores: ['node_modules/**', '.nuxt/**', '.output/**', 'test-results/**', 'playwright-report/**', 'public/vendor/**', 'app/generated/**'],
}).append({
  rules: {
    // The SSR track intentionally renders backend-produced HTML via v-html:
    // article/channel bodies are OWASP-sanitised server-side (see the
    // ArticleBody trust-boundary note) and search highlights only carry
    // Lucene's <em> wraps.
    'vue/no-v-html': 'off',
    // Align with Prettier, which always writes `<input />` for void elements.
    'vue/html-self-closing': ['warn', { html: { void: 'always', normal: 'always', component: 'always' } }],
  },
});
