import { Editor } from 'tinymce';
import { doAction } from '../core/actions';

const register = (editor: Editor, defaultOptions: any): void => {
  editor.addCommand(defaultOptions.id, () => {
    doAction(editor, defaultOptions);
  });
};

export { register };
