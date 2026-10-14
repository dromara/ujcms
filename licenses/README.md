# Third-party licences

UJCMS contains and adapts material from other projects. `NOTICE` in the repository root lists the third-party
components identified so far, each with its copyright line; this directory holds the licence texts and the per-file
detail. The licence of UJCMS itself is not stated here: see `LICENSE` in the community edition or your licence
agreement for a commercial edition.

| File | Licence | Applies to |
|---|---|---|
| Apache-2.0.txt | Apache License 2.0 | MyBatis Generator, Spring Security, Nimbus JOSE + JWT, MyBatis-Plus, Lucene, FreeMarker, Thymeleaf, Tomcat, Spring Framework, Liquibase, Quartz, Material Symbols, PDF.js, Maven Wrapper, tinymce-vue; most Java libraries in the binary distributions |
| MIT.txt | MIT License (copyright lines are in NOTICE) | Alpine.js, PhotoSwipe, Tailwind CSS, vue-element-admin, bpmn-js-i18n, bpmn-js-examples, bpmn-js-properties-panel, Ant Design Icons, Feather, TinyMCE 6 and its zh-Hans language pack, wps2md; most npm packages in the console build |
| OFL-1.1.txt | SIL Open Font License 1.1 | KaTeX fonts in the console build |
| bpmn-io.txt | bpmn.io license | bpmn-js (including its bpmn-font) |
| BSD-2-Clause.txt, BSD-3-Clause.txt | BSD licences (template texts; copyright lines are in the generated inventories) | PostgreSQL and HighGo JDBC drivers, commonmark, XStream, dom4j, curvesapi, libwebp, highlight.js, zrender and others in the binary distributions |
| EPL-2.0.txt | Eclipse Public License 2.0 | Logback, jakarta.annotation, jakarta.json, Parsson (binary distributions; the EPL option of their dual licence) |
| EDL-1.0.txt | Eclipse Distribution License 1.0 (BSD-3-Clause) | jakarta.mail, Angus Mail, jakarta.activation (binary distributions) |
| MPL-2.0.txt | Mozilla Public License 2.0 | LibreOffice UNO jars (commercial binaries); lightningcss at build time only |
| LGPL-2.1.txt | GNU LGPL 2.1 | MariaDB Connector/J (binary distributions; an unmodified, separately replaceable jar) |
| CC0-1.0.txt | CC0 1.0 | HdrHistogram, LatencyUtils (binary distributions, via micrometer) |
| BouncyCastle.txt | Bouncy Castle licence (MIT-style) | Bouncy Castle (binary distributions) |
| Dameng-JDBC.txt | Dameng JDBC Using License | Dameng JDBC driver (binary distributions; reproduced as the licence requires) |
| IU-Extreme-Lab-1.2.txt | Indiana University Extreme! Lab Software License 1.2 | mxparser (commercial T3 binaries, via weixin-java) |
| jsbn.txt | jsbn licence (MIT plus notice retention) | jsbn inside sm-crypto (console build and default theme vendor build) |
| none (https://creativecommons.org/licenses/by-sa/2.5/) | Creative Commons Attribution-ShareAlike 2.5 | Sitemaps.org protocol schemas (test data only) |

## Files

One row per file. Paths use the prefixes of `NOTICE`:

| Prefix | Directory |
|---|---|
| `common/` | `ujcms-common/src/main/java/com/ujcms/common/` |
| `generator/` | `ujcms-code-generator/src/main/java/com/ujcms/generator/mybatis/` |
| `cms/` | `ujcms-cms/src/main/` |
| `console/` | `ujcms-ui-console/` |
| `theme/` | `ujcms-starter/src/main/webapp/themes/1/default/` |

Not every distribution contains every module; a row applies where its file is present. An empty last column means
that the changes were made before 2026-04-19, the start of this repository's history, and are not itemised; later
changes are in the VCS log.

### Modified copies with the upstream header and a change note

These files are in substance the upstream file. Each starts with the upstream notice, followed by a note that names
the upstream file. MyBatis Generator classes are given relative to `org.mybatis.generator`.

| File | Upstream | Licence | Changes |
|---|---|---|---|
| `generator/MyBatisGenerator.java` | MyBatis Generator 1.4.2, `api.MyBatisGenerator` | Apache-2.0 | |
| `generator/XmlFileMergerJaxp.java` | MyBatis Generator 1.4.2, `internal.XmlFileMergerJaxp` | Apache-2.0 | |
| `generator/JavaMapperGenerator.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.SimpleJavaClientGenerator` | Apache-2.0 | |
| `generator/XmlMapperGenerator.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.SimpleXMLMapperGenerator` | Apache-2.0 | |
| `generator/mapper/MapperDeleteMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.DeleteByPrimaryKeyMethodGenerator` | Apache-2.0 | |
| `generator/mapper/MapperInsertMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.InsertMethodGenerator` | Apache-2.0 | |
| `generator/mapper/MapperSelectAllMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.SelectAllMethodGenerator` | Apache-2.0 | |
| `generator/mapper/MapperSelectMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.SelectByPrimaryKeyMethodGenerator` | Apache-2.0 | |
| `generator/mapper/MapperUpdateMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.UpdateByPrimaryKeyWithoutBLOBsMethodGenerator` | Apache-2.0 | |
| `generator/mapper/MapperUpdateBaseMethod.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.javamapper.elements.UpdateByPrimaryKeyWithoutBLOBsMethodGenerator` | Apache-2.0 | |
| `generator/xml/XmlBaseResultMapElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.ResultMapWithoutBLOBsElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlResultMapElement.java` | MyBatis Generator 1.4.2, package `codegen.mybatis3.xmlmapper.elements` (the exact class is not confirmed, so the file names the package) | Apache-2.0 | |
| `generator/xml/XmlBlobResultMapElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.ResultMapWithBLOBsElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlColumnListElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.BaseColumnListElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlInsertElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.InsertElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlSelectAllElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.SimpleSelectAllElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlSelectElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.SimpleSelectByPrimaryKeyElementGenerator` | Apache-2.0 | |
| `generator/xml/XmlUpdateElement.java` | MyBatis Generator 1.4.2, `codegen.mybatis3.xmlmapper.elements.UpdateByPrimaryKeyWithoutBLOBsElementGenerator` | Apache-2.0 | |
| `common/security/Pbkdf2WithHmacSm3PasswordEncoder.java` | Spring Security 5.7.11, `org.springframework.security.crypto.password.Pbkdf2PasswordEncoder` | Apache-2.0 | HMAC-SM3 as the pseudo-random function; the hash is Base64-encoded by default |
| `common/security/AbstractLoginConfigurer.java` | Spring Security, `org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer` | Apache-2.0 | |
| `common/security/jwt/HmacSm3JwsSigner.java` | Nimbus JOSE + JWT, `com.nimbusds.jose.crypto.MACSigner` and `com.nimbusds.jose.crypto.impl.MACProvider` | Apache-2.0 | signs with HMAC-SM3 |
| `common/security/jwt/HmacSm3JwsVerifier.java` | Nimbus JOSE + JWT, `com.nimbusds.jose.crypto.MACVerifier` and `com.nimbusds.jose.crypto.impl.MACProvider` | Apache-2.0 | verifies HMAC-SM3 signatures |
| `common/lucene/StringNormsField.java` | Apache Lucene 8.11.4, `org.apache.lucene.document.StringField` | Apache-2.0 | norms are kept |
| `common/freemarker/SimpleTemplateExceptionHandler.java` | Apache FreeMarker 2.3.34, `freemarker.template.TemplateExceptionHandler` (`HTML_DEBUG_HANDLER`) | Apache-2.0 | prints the escaped message without the stack trace |
| `cms/resources/db/changelog/liquibase.quartz.init.xml` | Quartz Scheduler, `quartz/src/main/resources/org/quartz/impl/jdbcjobstore/liquibase.quartz.init.xml` | Apache-2.0 | the postgresql entry of the `blob_type` property also lists kingbase8, kingbase, opengauss and highgo |
| `console/src/components/bpmnjs/palette/FlowablePaletteProvider.js` | bpmn-js, `lib/features/palette/PaletteProvider.js` | bpmn.io | creates user tasks instead of tasks; the data object and data store entries were removed |
| `console/src/components/bpmnjs/palette/index.js` | bpmn-js, `lib/features/palette/index.js` | bpmn.io | registers the provider above |
| `console/src/components/bpmnjs/context-pad/FlowableContextPadProvider.js` | bpmn-js, `lib/features/context-pad/ContextPadProvider.js` | bpmn.io | appends user tasks instead of tasks |
| `console/src/components/bpmnjs/context-pad/index.js` | bpmn-js, `lib/features/context-pad/index.js` | bpmn.io | registers the provider above |
| `console/src/components/bpmnjs/custom-translate/translations.ts` | bpmn-js-i18n, `translations/zn.js` | MIT | entries below the marker comment were added |
| `console/src/components/bpmnjs/custom-translate/customTranslate.ts` | bpmn-js-examples, `i18n/src/customTranslate/customTranslate.js` | MIT | typed for TypeScript |
| `console/src/components/Tinymce/utils.ts` | tinymce-vue, `src/main/ts/Utils.ts` | Apache-2.0 | |

### Files with a "portions derived from" note

These files are written for UJCMS and contain an adapted method or fragment. The note sits at the class or method
concerned.

| File | Upstream | Licence | Portion |
|---|---|---|---|
| `generator/IntrospectedTableSimpleImpl.java` | MyBatis Generator 1.4.2, `IntrospectedTableMyBatis3SimpleImpl` and `IntrospectedTableMyBatis3Impl` | Apache-2.0 | portions of the class, modified |
| `generator/Java8TypeResolver.java` | MyBatis Generator 1.4.2, `JavaTypeResolverDefaultImpl` | Apache-2.0 | portions of the class, modified |
| `generator/model/SuperBaseModel.java` | MyBatis Generator 1.4.2, `SimpleModelGenerator` | Apache-2.0 | portions of the class, modified |
| `generator/plugin/JavaAnnotationPlugin.java` | MyBatis Generator 1.4.2, `org.mybatis.generator.plugins.MapperAnnotationPlugin` | Apache-2.0 | method `clientGenerated`, modified |
| `common/db/identifier/SnowflakeSequence.java` | MyBatis-Plus, `Sequence.java` | Apache-2.0 | some names, argument checks and bit arithmetic; the class was rewritten on that basis |
| `common/freemarker/FormatMethod.java` | Thymeleaf (thymeleaf-extras-java8time), `TemporalObjects#zonedTime` | Apache-2.0 | method `zonedTime`; `OffsetDateTime` is converted with `atZoneSameInstant` |
| `common/web/DirectoryRedirectInterceptor.java` | Apache Tomcat, `DefaultServlet#doDirectoryRedirect` | Apache-2.0 | the directory redirect, adapted |
| `common/web/Servlets.java` | Apache Tomcat, `DefaultServlet#getRelativePath`; Spring Framework, `UriComponentsBuilder#query` | Apache-2.0 | two notes: method `getRelativePath` (one-argument overload); the pattern and parsing in `parseQueryString` |
| `common/web/Entities.java` | Spring Framework, `BeanUtils#copyProperties` | Apache-2.0 | the property copy, adapted |
| `common/db/liquibase/datatype/JsonType.java` | Liquibase 4.23.2, `CharType#objectToSql` | Apache-2.0 | method `objectToSql`, adapted |
| `cms/java/com/ujcms/cms/core/web/support/ConditionalResponses.java` | Spring Framework, `ShallowEtagHeaderFilter#generateETagHeaderValue` | Apache-2.0 | method `weakEtag`, adapted on 2026-09-09 |
| `ujcms-commercial/src/main/java/com/ujcms/cms/commercial/t1/liquibase/datatype/DateTimeWithTimeZoneType.java` | Liquibase 4.23.2, `liquibase.datatype.core.DateTimeType#toDatabaseDataType` | Apache-2.0 | method `toDatabaseDataType`, adapted; commercial editions only |
| `console/src/components/Tinymce/Tinymce.vue` | tinymce-vue, `Editor.ts`; TinyMCE 6, the editor core's default upload handler | Apache-2.0 (tinymce-vue); MIT (TinyMCE 6) | two notes: the component structure (props, initialisation and destruction timing); the image upload handler |
| `console/src/components/MarkdownEditor/utils.ts` | TinyMCE 6, the editor core's default upload handler | MIT | the image upload handler (`uploadOne`) |
| `console/src/layout/composables/useResizeHandler.ts` | vue-element-admin | MIT | rewritten for Vue 3 |
| `console/src/styles/transition.scss` | vue-element-admin | MIT | rewritten for Vue 3 |
| `console/src/components/BreadCrumb/index.vue`, `console/src/layout/components/AppMain.vue`, `console/src/layout/components/AppTab.vue`, `console/src/permission.ts`, `console/src/views/RefreshPage.vue` | vue-element-admin (breadcrumb, main view, tab bar, route guard, refresh view) | MIT | smaller adapted portions, heavily rewritten; no note in the files |
| `console/src/components/bpmnjs/properties-panel/utils.ts` | bpmn-js-properties-panel, the helper `createElement` | MIT | `createElement` follows the upstream helper of the same name |
| `theme/templates/article_wenku.html` | Material Symbols (Google); PDF.js, `examples/components/simpleviewer` | Apache-2.0 | two items in one note: four toolbar icons (zoom out, zoom in, fit width, full screen), modified; the viewer set-up script follows the sample |

### Vendored, generated and binary files

These files are copies, build output or assets. Where the format allows it they carry a banner or a comment;
otherwise the notice is the `NOTICE` entry and, where listed below, a text file beside the asset.

| File | Upstream | Licence | State |
|---|---|---|---|
| `ujcms-cms/src/test/resources/sitemap-xsd/sitemap.xsd` | Sitemaps.org protocol 0.9 schema | CC BY-SA 2.5 | unmodified test data; see the `README.md` beside it |
| `ujcms-cms/src/test/resources/sitemap-xsd/siteindex.xsd` | Sitemaps.org protocol 0.9 schema | CC BY-SA 2.5 | unmodified test data; see the `README.md` beside it |
| `mvnw` | Apache Maven Wrapper 3.3.4 | Apache-2.0 | unmodified, upstream header intact |
| `mvnw.cmd` | Apache Maven Wrapper 3.3.4 | Apache-2.0 | unmodified, upstream header intact |
| `theme/assets/js/alpine.min.js` | Alpine.js 3.14.1, CDN build | MIT | one banner line added; the sha256 of the original file is in the theme's `THIRD-PARTY-NOTICES.md` |
| `theme/assets/js/photoswipe/photoswipe.esm.min.js` | PhotoSwipe 5.4.4 | MIT | vendored release file, banner intact |
| `theme/assets/js/photoswipe/photoswipe-lightbox.esm.min.js` | PhotoSwipe 5.4.4 | MIT | vendored release file, banner intact |
| `theme/assets/js/photoswipe/photoswipe.css` | PhotoSwipe 5.4.4 | MIT | vendored release file, banner intact |
| `theme/assets/css/style.css` | Tailwind CSS 4.3.2 | MIT | compiled output that includes Tailwind's base styles, banner intact |
| `theme/snippets/icon.html` | Material Symbols (Google), taken from @iconify/json | Apache-2.0 | generated by `theme/_src/build-icons.mjs`, which writes the attribution into the file header; icon bodies unmodified |
| `ujcms-ui-ssr-vue/app/components/AppHeader.vue` | Material Symbols (Google) | Apache-2.0 | inline icon paths, unmodified; one comment line |
| `ujcms-ui-ssr-vue/app/components/HeaderNav.vue` | Material Symbols (Google) | Apache-2.0 | inline icon paths, unmodified; one comment line |
| `ujcms-ui-ssr-vue/app/components/article/ArticleListItem.vue` | Material Symbols (Google) | Apache-2.0 | inline icon paths, unmodified; one comment line |
| `console/src/views/config/components/OauthProviderCard.vue` | Ant Design Icons (wechat-filled, qq-outlined, weibo-outlined) | MIT | three glyph paths, unmodified; one comment line |
| `console/src/components/MarkdownEditor/MarkdownEditor.vue` | Feather, the folder icon | MIT | icon path, unmodified; one comment line |
| `console/public/tinymce/langs/zh-Hans.js` | TinyMCE 6 community language pack (zh-Hans), `tinymce-i18n@26.9.28` `langs6/zh-Hans.js` (sha256 `50af56ee1aedc98cad0339472d536127923d70d7fba17ff1467fc42f855df9b8`) | MIT (the terms of TinyMCE 6) | banner line added on 2026-10-12; no translation changed. Replaced the TinyMCE 5 packs (LGPL-2.1) `zh_CN.js` / `zh_TW.js` |
| `ujcms-commercial/src/test/resources/word-import/wps-office-12-camping-guide.wps` | wps2md, `tests/sample.wps` | MIT | test data, not part of the built application; see the `README.md` beside it; commercial editions only |

The default theme's `THIRD-PARTY-NOTICES.md` travels with the theme package, and `SEED-MEDIA.md` beside it names the
font whose glyph subset is embedded in the sample PDF. The demo theme `journal`
(`ujcms-starter/src/main/webapp/themes/1/journal/`, distributed only with the demo site) is covered by its own
`THIRD-PARTY-NOTICES.md`: Lucide icons in `snippets/icon.html` (ISC) and QR Code Generator for JavaScript in
`assets/js/qrcode.js` (MIT).

The themes' own files are first-party. The demo themes (`default`, `journal`, `mobile`, the `demo-data` package) are
MIT-licensed, each with a `LICENSE` at its root whose scope section keeps the third-party components and the demo media
outside the grant; a paid first-party theme carries the UJCMS theme licence (`LICENSE.md`, manifest
`LicenseRef-UJCMS-Theme-<version>`) and is distributed through the theme marketplace only.

## Binary distributions

A jar, war or Docker image also carries the libraries resolved from Maven and npm. They are not itemised by hand;
three generated files travel with every binary distribution:

| File | Produced by | Contents |
|---|---|---|
| `META-INF/THIRD-PARTY.txt` (inside the jar / `WEB-INF/classes` of the war) | `license-maven-plugin` in `ujcms-starter/pom.xml`, phase `generate-resources` | every Java library with version and licence; names normalised to SPDX identifiers by `licenseMerges`; a licence outside `includedLicenses` fails the build |
| `cp/THIRD-PARTY-LICENSES.txt` | `rollup-plugin-license` in `ujcms-ui-console/vite.config.ts` | every npm package bundled into the console, with author, repository and licence text; a licence outside the allow-list fails the build |
| `bom.json` (beside the jar / war in the release package; copied into the Docker image) | `cyclonedx-maven-plugin` in `ujcms-starter/pom.xml`, the Spring Boot parent's `generate-resources` execution; a copy of the `META-INF/sbom/application.cdx.json` inside the archive | CycloneDX software bill of materials |

Two hand-kept tables feed the Java inventory: `ujcms-starter/src/license/override-THIRD-PARTY.properties` (dependencies
whose pom declares a licence different from the one in the jar, e.g. the Dameng driver; entries are pinned to a version)
and `ujcms-starter/src/license/THIRD-PARTY.properties`, which the plugin would create only if a dependency declared no
licence at all (none does today, so that file does not exist). `bom.json` takes its licence fields from the poms as declared, so for the Dameng and
HighGo drivers `THIRD-PARTY.txt` is the authoritative record. `scripts/check-licenses.mjs` checks the whole npm
production trees at lint time. xmlpull (commercial T3 binaries, via weixin-java) declares itself public domain and has
no licence text. Logos, theme previews, thumbnails and the console favicon are UJCMS's own artwork and need no row here.

## Adding third-party material

Keep the upstream header (or add a banner), note what you changed, add an entry to `NOTICE` and a row to the tables
above, and add the licence text here if it is a new licence. A new Maven or npm dependency needs no entry here, but it
must pass the build gates: a licence name the Java gate does not know goes into `licenseMerges` (if it is a spelling of
a known licence) or `includedLicenses` (after confirming it may ship in a proprietary edition and adding its text here);
copyleft libraries (GPL, AGPL, LGPL, SSPL, BUSL) are not added to `ujcms-commercial` without a decision record.
