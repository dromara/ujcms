import { imageUploadUrl } from '@/api/config';
import { getAuthHeaders } from '@/utils/auth';
import { getSiteHeaders } from '@/utils/common';
import Editor from '@toast-ui/editor';

/**
 * 在对话框中使用编辑器时，点击更多工具按钮后，再点击页面其它地方，弹出的工具不会消失。需要认为的抛出一个点击事件。
 */
export const clickOutside = (event: Event) => {
  if (event.bubbles || !event.cancelable || event.composed) {
    const myEvent = new Event('click', { bubbles: false, cancelable: true, composed: false });
    document.dispatchEvent(myEvent);
  }
};

export const toggleFullScreen = (editor: Editor, element: HTMLElement, height: string): void => {
  const style = element.style;
  if (style.height !== '100vh') {
    style.height = '100vh';
    style.width = '100vw';
    style.position = 'fixed';
    style.zIndex = '10000000000';
    style.top = '0px';
    style.left = '0px';
    style.backgroundColor = 'white';
    editor.changePreviewStyle('vertical');
  } else {
    style.height = height;
    style.width = '';
    style.position = '';
    style.zIndex = '';
    style.top = '';
    style.left = '';
    style.backgroundColor = '';
    editor.changePreviewStyle('tab');
  }
};

export const addImageBlobHook = (blob: Blob | File, callback: any): void => {
  const xhr = new XMLHttpRequest();
  xhr.open('POST', imageUploadUrl);
  Object.entries(getSiteHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));

  // xhr.upload.onprogress = (e) => {
  //   (e.loaded / e.total) * 100;
  // };

  xhr.onload = () => {
    if (xhr.status === 403) {
      ElMessageBox.alert(`HTTP Error: ${xhr.status}`, { type: 'warning' });
      return;
    }

    if (xhr.status < 200 || xhr.status >= 300) {
      ElMessageBox.alert(`HTTP Error: ${xhr.status}`, { type: 'warning' });
      return;
    }

    const json = JSON.parse(xhr.responseText);

    if (!json || typeof json.url !== 'string') {
      ElMessageBox.alert(`Invalid JSON: ${xhr.responseText}`, { type: 'warning' });
      return;
    }
    callback(json.url);
  };

  xhr.onerror = () => {
    ElMessageBox.alert(`Image upload failed due to a XHR Transport error. Code: ${xhr.status}`, { type: 'warning' });
  };

  const formData = new FormData();
  formData.append('file', blob);

  Object.entries(getAuthHeaders()).forEach(([key, value]: any) => xhr.setRequestHeader(key, value));
  xhr.send(formData);
};
