/**
 * 视频、音频元数据提取。利用浏览器自身的解码能力提取时长和视频截图，服务器端无须安装任何多媒体组件。
 * 系统允许上传的视频、音频格式（mp4,webm,ogg,mp3,wav）均为浏览器原生支持的格式。
 */
export interface MediaMeta {
  // 时长（秒）
  duration?: number;
  // 视频截图
  image?: Blob;
}

// 元数据提取超时时间。超时则放弃提取，不影响文件上传
const EXTRACT_TIMEOUT = 15000;

// 视频截图时间点：视频 1/4 处，最大 20 秒。加 0.001 防止为 0，部分浏览器在 currentTime 不变时不触发 seeked 事件
const captureTime = (duration: number): number => Math.max(Math.min(duration / 4, 20), 0.001);

export const extractAudioMeta = (file: File): Promise<MediaMeta> =>
  new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    const finish = (meta: MediaMeta) => {
      URL.revokeObjectURL(url);
      resolve(meta);
    };
    const timer = setTimeout(() => finish({}), EXTRACT_TIMEOUT);
    audio.preload = 'metadata';
    audio.onloadedmetadata = () => {
      clearTimeout(timer);
      finish(Number.isFinite(audio.duration) ? { duration: Math.round(audio.duration) } : {});
    };
    audio.onerror = () => {
      clearTimeout(timer);
      finish({});
    };
    audio.src = url;
  });

export const extractVideoMeta = (file: File): Promise<MediaMeta> =>
  new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const video = document.createElement('video');
    const meta: MediaMeta = {};
    const finish = () => {
      URL.revokeObjectURL(url);
      video.removeAttribute('src');
      resolve(meta);
    };
    const timer = setTimeout(finish, EXTRACT_TIMEOUT);
    const done = () => {
      clearTimeout(timer);
      finish();
    };
    video.preload = 'metadata';
    video.muted = true;
    video.playsInline = true;
    video.onerror = done;
    video.onloadedmetadata = () => {
      if (!Number.isFinite(video.duration)) {
        done();
        return;
      }
      meta.duration = Math.round(video.duration);
      video.currentTime = captureTime(video.duration);
    };
    video.onseeked = () => {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      const context = canvas.getContext('2d');
      if (canvas.width <= 0 || canvas.height <= 0 || context == null) {
        done();
        return;
      }
      context.drawImage(video, 0, 0);
      canvas.toBlob(
        (blob) => {
          if (blob != null) {
            meta.image = blob;
          }
          done();
        },
        'image/jpeg',
        0.85,
      );
    };
    video.src = url;
  });

export const extractMediaMeta = (file: File, type: 'video' | 'audio'): Promise<MediaMeta> =>
  type === 'video' ? extractVideoMeta(file) : extractAudioMeta(file);
