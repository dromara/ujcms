# 演示数据媒体来源说明 / Seed media provenance

`seed/content/assets/` 下的 79 个文件均由 UJCMS 项目于 2026 年 10 月自行制作，不包含第三方的照片、海报、
产品图、视频、文档或独立的字体文件（示例 PDF 内嵌了一款开源字体的字形子集，见下表），制作过程中也没有把任何
第三方图片作为输入。

| 文件 | 数量 | 制作方式 |
| --- | --- | --- |
| `img/**`（下面两行以外） | 69 | **AI 生成**。项目自写的文字提示词经 OpenAI 图像生成（Codex CLI）得到 60 张母图，再用 ImageMagick 裁剪、缩放并重新编码 |
| `img/media-software/`、`img/network-software/`、`img/system-tools/` | 5 | 程序绘制的通用图标（ImageMagick），不是任何软件的官方标志 |
| `img/education/*.png` | 2 | 由下面的示例 PDF 页面渲染 |
| `media/video_demo.mp4` | 1 | 用 FFmpeg 由其中 5 张 AI 生成图片合成，无声 |
| `media/freemarker_manual_zh_cn.pdf` | 1 | 项目自写的示例文档，Typst 排版，只嵌入 Noto Sans CJK SC 字体家族（正文与等宽，SIL OFL 1.1）的字形子集 |
| `media/file_demo.zip` | 1 | 只含一个空文件的占位压缩包 |

## AI 生成标识

AI 生成的图片，以及由它们合成的视频，在文件元数据中带有标识：

- 图片：注释 `AI-generated image (OpenAI image generation via Codex CLI), UJCMS demo content`，
  以及 XMP `Iptc4xmpExt:DigitalSourceType = trainedAlgorithmicMedia`。
- 视频：`comment` 标签。

除此之外的元数据（相机信息、色彩配置、生成工具写入的其它信息）已全部去除。

## 说明

- 图片均为通用示意图，不呈现可辨认的真实人物面部、品牌、商标或文字；画面中出现的人物均为人工智能生成的虚构形象，
  不对应任何真实的人。
- 文件名以及演示文章的标题、正文均为 UJCMS 项目为演示而撰写的虚构内容，不指向任何真实的人物、企业、品牌、产品、
  影视作品、机构或事件；其中的名称、数字与情节如与现实雷同，纯属巧合。
- 媒体文件的使用范围见 `LICENSE` 的「适用范围」：仅作演示占位，站点上线前应替换为自有素材；主题自身的文件按 MIT 授权。
- 对其中内容有疑问，请通过 <https://www.ujcms.com> 联系。

## English summary

All 79 files under `seed/content/assets/` were produced by the UJCMS project in October 2026. No third-party
photograph, artwork, video, document or standalone font file is included, and no third-party image was used as an
input.
69 pictures are **AI-generated** from the project's own text prompts (OpenAI image generation through Codex CLI),
then cropped, resized and re-encoded; they carry an "AI-generated" comment and the XMP digital source type
`trainedAlgorithmicMedia`. Five software icons are drawn programmatically, two document covers are rendered
from the sample PDF, the video is assembled from five of the pictures, the PDF is a self-written sample set in
the Noto Sans CJK SC family (text and mono, SIL OFL 1.1) with only glyph subsets embedded, and the zip is an
empty placeholder. File names and demo article text are
fictional, written by the UJCMS project for demonstration; they do not refer to any real person, company, brand,
product, film, organisation or event. People shown in the pictures are AI-generated and are not real persons.
