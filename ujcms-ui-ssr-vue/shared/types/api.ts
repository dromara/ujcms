/**
 * UJCMS API contract types — ported 1:1 from the canonical React track
 * (`ujcms-ui-ssr-react/src/api/types.ts`).
 *
 * Source of truth for the JSON contract is the Spring entity classes plus
 * Jackson @JsonView annotations (see docs/spec/template-spec/api-inventory.md).
 * Add fields here only as they are needed; do not pre-declare the entire
 * domain.
 *
 * Lives in `shared/` so both the Nuxt app and the Nitro server routes can
 * import the same contract.
 */

export interface CsrfToken {
  headerName: string;
  parameterName: string;
  token: string;
}

/**
 * Spring serialises Long entity IDs as strings (Jackson @JsonFormat) so that
 * IDs > 2^53 don't lose precision in JS. Treat all `id` fields as opaque
 * strings.
 */
export interface Site {
  id: string;
  name?: string;
  fullName?: string;
  url?: string;
  protocol?: string;
  domain?: string;
  subDir?: string | null;
  seoTitle?: string;
  seoKeywords?: string;
  seoDescription?: string;
  customs?: Record<string, unknown>;
  /**
   * `Site.render_mode_` — admin-UX gate that decides which set of layout
   * controls operators see on Channel / Article / Topic forms. Has no
   * effect on App-track dispatch at runtime. One of `template` | `ssr` |
   * `both` (default `template`). Added in V11.3.
   */
  renderMode?: 'template' | 'ssr' | 'both' | null;
  /** Operator-set logo URL/path. Empty for default theme logo (`/logo.png`). */
  logo?: string | null;
  /**
   * Site-level message-board settings (`Site.MessageBoard`). Exposed via the
   * `messageBoard` getter; persisted as a JSON string in `messageBoardSettings`.
   * Used by the SSR track to gate the create page on `loginRequired`.
   */
  messageBoard?: {
    enabled?: boolean;
    loginRequired?: boolean;
  };
  [key: string]: unknown;
}

/**
 * Shape of `GET /api/user/me` (Spring's `UserController.MeUser` record).
 * The endpoint returns `{}` for anonymous callers; `app/api/session.ts →
 * fetchCurrentUser()` normalises the empty case to `null`.
 *
 * Spring serialises `id` as a string (Jackson Long-as-String). Other size
 * variants of avatar (`smallAvatar`/`mediumAvatar`/`largeAvatar`) are
 * derived URLs that may be null when no avatar is set.
 */
export interface CurrentUser {
  id: string;
  username: string;
  nickname?: string | null;
  realName?: string | null;
  gender?: number;
  birthday?: string | null;
  location?: string | null;
  bio?: string | null;
  email?: string | null;
  mobile?: string | null;
  avatar?: string | null;
  smallAvatar?: string | null;
  mediumAvatar?: string | null;
  largeAvatar?: string | null;
  homepage?: string;
  /** True iff the user has a local password set (false for pure-OAuth accounts). */
  hasPassword?: boolean;
  created?: string;
}

export interface UjcmsConfig {
  security?: Record<string, unknown>;
  register?: Record<string, unknown>;
  upload?: Record<string, unknown>;
  customs?: Record<string, unknown>;
  [key: string]: unknown;
}

export interface ApiBody<TResult = unknown> {
  status: number;
  result?: TResult;
  message?: string;
}

/**
 * Spring serialises the Long id as a string (`@JsonFormat`); other numeric
 * fields stay numeric. Treat `id` as opaque.
 */
export interface Channel {
  id: string;
  siteId?: string;
  parentId?: string | null;
  name?: string;
  slug?: string;
  url?: string;
  dynamicUrl?: string;
  title?: string;
  seoTitle?: string;
  seoKeywords?: string;
  seoDescription?: string;
  /** Variant template name (`channel_video`, `channel_about`, …). Empty/null → base `channel`. */
  channelTemplate?: string | null;
  /** Variant template name for child article rendering (`article_download`, …). Empty/null → base `article`. */
  articleTemplate?: string | null;
  /**
   * V11.3 SSR layout variant key — matches a row in the SSR layout-variants catalog
   * (`channel` surface). App-track dispatcher reads this; falls back
   * to `default` for empty / unknown values. Distinct from `channelTemplate`
   * (theme template file path).
   */
  channelLayout?: string | null;
  /**
   * V11.3 SSR layout variant key for child article rendering — matches a row
   * in the SSR layout-variants catalog (`article` surface). Per-article
   * `Article.articleLayout` overrides this when set.
   */
  articleLayout?: string | null;
  nav?: boolean;
  targetBlank?: boolean;
  hasChildren?: boolean;
  depth?: number;
  order?: number;
  orderDesc?: boolean;
  pageSize?: number;
  views?: string;
  /** Channel body HTML (used by `channel_about` to render an inline article-like body). */
  text?: string;
  parent?: ChannelRef | null;
  paths?: Array<{ id: string; slug?: string; name?: string; url?: string; nav?: boolean }>;
  articleModel?: {
    id?: string;
    name?: string;
    fieldList?: Array<{
      code: string;
      name?: string;
      type?: string;
      dataType?: string;
      dictTypeId?: string | null;
    }>;
  };
  customs?: Record<string, unknown>;
  [key: string]: unknown;
}

/**
 * Topic — independent of Channel in V11.2. Article order comes from the
 * curator (`ujcms_article_topic.order_`), not Article's own sticky/order.
 * Backend convention: {@code Topic.getName()} delegates to {@code getTitle()},
 * so both fields carry the same string on the wire.
 */
export interface Topic {
  id: string;
  siteId?: string;
  parentId?: string | null;
  topicModelId?: string;
  title?: string;
  /** Server-side {@code getName()} delegates to {@code getTitle()}. */
  name?: string;
  slug?: string;
  image?: string;
  pageSize?: number;
  orderDesc?: boolean;
  topicStaticPath?: string;
  seoTitle?: string;
  seoKeywords?: string;
  seoDescription?: string;
  topicTemplate?: string | null;
  /**
   * V11.3 SSR layout variant key — matches a row in the SSR layout-variants catalog
   * (`topic` surface). App-track dispatcher reads this; falls back
   * to `default` for empty / unknown values.
   */
  topicLayout?: string | null;
  url?: string;
  dynamicUrl?: string;
  parent?: { id: string; slug?: string; name?: string; url?: string } | null;
  /** Direct child topics. Hydrated by the full ResultMap on the slug lookup (`/api/topic/slug/{slug}`); list endpoint omits it. Each child only ships `id` / `url` / `name`. */
  children?: Array<{ id: string; name?: string; url?: string }>;
  paths?: Array<{ id: string; slug?: string; name?: string; url?: string }>;
  views?: string;
  customs?: Record<string, unknown>;
  [key: string]: unknown;
}

/**
 * Dictionary entry — operator-defined option for a `select`/`radio`/`checkbox`/
 * `multipleSelect` custom field. Drives facet-filter chip groups via
 * `/api/dict?typeId=<id>`.
 */
export interface DictEntry {
  id: string;
  typeId?: string;
  name?: string;
  /** Value to store/match (string in JSON, may be numeric semantically). */
  value?: string;
  order?: number;
  enabled?: boolean;
}

/**
 * Channel leader-board entry. Returned by `/api/leader-board?type={channel|org|user}`.
 * Both ranking columns ('栏目排行' + '组织排行') of the `channel_leader_board` page
 * use this same shape; `id` is the entity (channel/org/user) id.
 */
export interface LeaderBoardEntry {
  id: string;
  name?: string;
  count?: number;
}

export interface SiteViewBuffer {
  siteId?: string;
  todayViews?: number;
  yesterdayViews?: number;
  views?: number;
  [key: string]: unknown;
}

export interface ChannelRef {
  id: string;
  name?: string;
  slug?: string;
  url?: string;
  nav?: boolean;
  orderDesc?: boolean;
  paths?: Array<{ id: string; name?: string; slug?: string; url?: string; nav?: boolean }>;
}

export interface ArticleImageRef {
  url: string;
  description?: string;
}

export interface ArticleFileRef {
  name?: string;
  url?: string;
  size?: number;
}

export interface Article {
  id: string;
  siteId?: string;
  channelId?: string;
  order?: string;
  title?: string;
  subtitle?: string;
  shortTitle?: string;
  description?: string;
  seoTitle?: string;
  seoKeywords?: string;
  seoDescription?: string;
  /** Custom URL slug; empty → dynamic URL is `/article/{id}`, else `/article/{id}-{slug}` (or `/article/{slug}` in slug-only mode). */
  slug?: string;
  url?: string;
  dynamicUrl?: string;
  image?: string;
  source?: string;
  author?: string;
  publishDate?: string;
  modified?: string;
  views?: string;
  ups?: number;
  downs?: number;
  downloads?: number;
  weekViews?: number;
  yearViews?: string | number;
  channel?: ChannelRef;
  tags?: Array<{ id: string; name?: string }>;
  targetBlank?: boolean;
  videoTime?: string;
  /** Detail-only: HTML body (sanitised by backend OWASP). @JsonView(Whole.class). */
  text?: string;
  /** Detail-only: jsoup-extracted plain text. */
  plainText?: string;
  video?: string;
  audio?: string;
  /** Wenku PDF source (URL). `docOrig` is the original (unwatermarked) variant when present. */
  doc?: string;
  docOrig?: string;
  /** Primary downloadable file URL (article_download). */
  file?: string;
  fileName?: string;
  /** Computed server-side from `fileLength` bytes (e.g. "1 KB", "2.4 MB"). */
  fileSize?: string;
  /** Variant template path (e.g. `/1/default/templates/article_download`). Last segment is the variant name. */
  template?: string;
  /**
   * V11.3 SSR per-article layout override — matches a row in
   * the SSR layout-variants catalog (`article` surface). Resolves before
   * `channel.articleLayout`; empty falls through to the channel default.
   */
  articleLayout?: string | null;
  imageList?: ArticleImageRef[];
  fileList?: ArticleFileRef[];
  /** Operator-defined custom fields keyed by `Model.Field.code`; for `select`/`radio` the resolved label lives at `<code>` and the dict value at `<code>Key`. */
  customs?: Record<string, unknown>;
  [key: string]: unknown;
}

/**
 * Spring Boot 3 PagedModel shape. The four page-summary fields (size, number,
 * totalPages, totalElements) come back as **strings** because Spring serialises
 * Long as string for JS precision — handle via app/utils/pagination#toPageInt.
 */
export interface PagedModel<T> {
  content: T[];
  page?: {
    size?: number | string;
    number?: number | string;
    totalPages?: number | string;
    totalElements?: number | string;
  };
}

export interface Tag {
  id: string;
  siteId?: string;
  name?: string;
  description?: string | null;
  refers?: number;
  [key: string]: unknown;
}

/**
 * Elasticsearch / Lucene full-text search result. Mirrors EsArticle shape:
 * a flattened article projection with optional `highlight*` HTML fragments
 * around the matched terms.
 */
export interface EsArticle {
  id: string;
  title?: string;
  body?: string;
  highlightTitle?: string;
  highlightBody?: string;
  url?: string;
  image?: string;
  publishDate?: string;
  channel?: ChannelRef;
  [key: string]: unknown;
}

/**
 * `BlockItem` — operator-defined homepage focus / banner item. Selected by
 * a `block.alias` (commonly `'focus'`) and rendered as a carousel slide.
 * `url` is server-computed (referenced article URL falls back to assembled
 * `linkUrl`); `image` is the slide image, `targetBlank` opens in a new tab.
 */
export interface BlockItem {
  id: string;
  blockId?: string;
  articleId?: string | null;
  title?: string;
  subtitle?: string | null;
  description?: string | null;
  /** Server-computed (article URL or assembled linkUrl). */
  url?: string;
  image?: string | null;
  mobileImage?: string | null;
  video?: string | null;
  targetBlank?: boolean;
  enabled?: boolean;
  order?: number;
  /** Inclusive lower bound of the publish window; null = always-active. */
  startDate?: string | null;
  /** Exclusive upper bound of the publish window; null = no expiry. */
  endDate?: string | null;
  /** Operator-defined custom fields driven by the parent Block's blockItemModel. */
  customs?: Record<string, unknown>;
}

/**
 * `Block` — slot definition. Items inside a Block share its schema flags and,
 * when `blockItemModelId` is set, the same operator-defined custom-field shape.
 * `title` / `moreUrl` are operator-facing public chrome (section heading +
 * "show all" link); fall back to `name` when `title` is absent.
 */
export interface Block {
  id: string;
  siteId?: string | null;
  name?: string;
  alias?: string;
  scope?: number;
  /** Public-facing section heading; prefer over `name` in the renderer. */
  title?: string | null;
  /** Optional "show all" link rendered alongside the heading. */
  moreUrl?: string | null;
  /** Category grouping (e.g. `'friend-links'`) for `GET /api/block?category=`. */
  category?: string | null;
  /** FK to the Model whose custom fields apply to items in this Block. */
  blockItemModelId?: string | null;
  enabled?: boolean;
  recommendable?: boolean;
  order?: number;
}

/**
 * Vote / Survey shared status codes (Java side: `Vote.STATUS_*` /
 * `Survey.STATUS_*`). Computed server-side from `enabled` + `startDate` +
 * `endDate`, so the same code drives the `<VoteStatusBadge>`.
 */
export const VOTE_STATUS = {
  NORMAL: 0,
  DISABLED: 1,
  NOT_STARTED: 2,
  HAS_ENDED: 3,
} as const;

export const VOTE_MODE = {
  COOKIE: 1,
  IP: 2,
  USER: 3,
} as const;

export interface VoteOption {
  id: string;
  voteId?: string;
  title?: string;
  count?: number;
  /** Server-computed `option.count * 100 / vote.total`. Decimal. */
  percent?: number;
  order?: number;
}

export interface Vote {
  id: string;
  siteId?: string;
  title?: string;
  description?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  created?: string;
  /** Days between repeats; > 0 caps re-vote interval, 0 = no repeat. */
  interval?: number;
  /** Total cast count (server-incremented via `/cast`). */
  times?: number;
  /** `Vote.MODE_COOKIE | MODE_IP | MODE_USER`. USER mode requires login. */
  mode?: number;
  multiple?: boolean;
  enabled?: boolean;
  /** Computed server-side; matches `VOTE_STATUS`. */
  status?: number;
  /** Detail view only — list view excludes options. */
  options?: VoteOption[];
}

export interface SurveyOption {
  id: string;
  itemId?: string;
  title?: string;
  count?: number;
  percent?: number;
  order?: number;
}

export interface SurveyItem {
  id: string;
  surveyId?: string;
  title?: string;
  /** True when this item is a free-text "essay" question (textarea). */
  essay?: boolean;
  multiple?: boolean;
  order?: number;
  options?: SurveyOption[];
}

export interface Survey {
  id: string;
  siteId?: string;
  title?: string;
  description?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  created?: string;
  mode?: number;
  times?: number;
  interval?: number;
  enabled?: boolean;
  status?: number;
  /** Detail view only — list view excludes items. */
  items?: SurveyItem[];
}

export interface MessageBoardType {
  id: string;
  siteId?: string;
  name?: string;
  order?: number;
}

export interface MessageBoard {
  id: string;
  siteId?: string;
  typeId?: string;
  userId?: string;
  replyUserId?: string | null;
  title?: string;
  text?: string;
  contact?: string | null;
  nickname?: string | null;
  phone?: string | null;
  email?: string | null;
  address?: string | null;
  open?: boolean;
  ip?: string;
  replied?: boolean;
  recommended?: boolean;
  status?: number;
  replyText?: string | null;
  created?: string;
  replyDate?: string | null;
  type?: { id: string; name?: string };
  user?: { id: string; username?: string; nickname?: string | null };
  replyUser?: { id: string; username?: string; nickname?: string | null } | null;
}

/**
 * `Model.Field` — operator-defined custom field schema. Drives
 * `<DynamicFormFields>` rendering. `type` is one of the `Model.TYPE_*` codes
 * on the Java side (`text`, `textarea`, `number`, `date`, `select`, `radio`,
 * `checkbox`, `multipleSelect`, `switch`, `imageUpload`, `videoUpload`,
 * `fileUpload`, `richEditor`, `markdownEditor`, ...).
 */
export interface ModelField {
  code: string;
  name?: string;
  type?: string;
  dataType?: string;
  precision?: number | null;
  dictTypeId?: string | null;
  /** True when the field stores its value in the clobs JSON (long text). */
  clob?: boolean;
  showInList?: boolean;
}

export interface ModelEntity {
  id: string;
  siteId?: string;
  name?: string;
  type?: string;
  customs?: string;
  /** Computed server-side from `customs` JSON; the source of `<DynamicFormFields>` props. */
  fieldList?: ModelField[];
}

export const FORM_TYPE_MODE = {
  GUEST: 0,
  FRONTEND: 1,
  BACKEND: 2,
} as const;

export interface FormType {
  id: string;
  siteId?: string;
  modelId?: string;
  name?: string;
  mode?: number;
  viewable?: boolean;
  enabled?: boolean;
  order?: number;
  /** `@JsonIncludeProperties({"id","name"})` — fieldList NOT included here. Fetch `/api/model/{id}` separately. */
  model?: { id: string; name?: string };
}

export interface Form {
  id: string;
  siteId?: string;
  typeId?: string;
  userId?: string;
  status?: number;
  ip?: string;
  created?: string;
  modified?: string;
  customs?: Record<string, unknown>;
  type?: FormType;
  user?: { id: string; username?: string; nickname?: string | null };
}

/**
 * Public user-profile shape returned by `GET /api/user/{id}` —
 * matches the `UserController.PublicUser` Java record. Fields are
 * privacy-trimmed: no email/mobile/ext/group fields (the full `User`
 * entity would leak those if returned directly). Spring's global Long-as-
 * String Jackson serializer applies, so `id` is a string here.
 */
export interface PublicUser {
  id: string;
  username: string;
  nickname?: string | null;
  gender?: number | null;
  birthday?: string | null;
  location?: string | null;
  bio?: string | null;
  avatar?: string | null;
  smallAvatar?: string | null;
  mediumAvatar?: string | null;
  largeAvatar?: string | null;
  homepage?: string;
  created?: string;
}

/**
 * Common action-result shape returned by the `/ssr-api/**` Nitro routes
 * (the Nuxt counterpart of the React track's server-action
 * `FormActionState`):
 *  - `error`: failure message (red banner)
 *  - `ok`: success flag (green banner using `message`)
 *  - `status`: Spring `Body.status` passthrough; 100 = awaiting review (amber)
 *  - `message`: success/info text (Chinese, comes from the Spring side)
 *  - `redirect`: client should `navigateTo` here after refreshing session state
 */
export interface ActionState {
  error?: string;
  ok?: boolean;
  status?: number;
  message?: string;
  redirect?: string;
}
