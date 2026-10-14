/*
 * Derived from bpmn-js-examples (https://github.com/bpmn-io/bpmn-js-examples), i18n/src/customTranslate/customTranslate.js,
 * by the bpmn.io contributors. MIT License (licenses/MIT.txt).
 *
 * Modified for UJCMS: typed for TypeScript. First modified before 2026-04-19.
 */
import translations from './translations';
/**
 * https://github.com/bpmn-io/bpmn-js-examples/blob/main/i18n/src/customTranslate/customTranslate.js
 */
export default function customTranslate(template, replacements) {
  replacements = replacements || {};

  // Translate
  template = translations[template] || template;

  // Replace
  return template.replace(/{([^}]+)}/g, function (_, key) {
    return replacements[key] || '{' + key + '}';
  });
}
