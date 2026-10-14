import tinymce from 'tinymce';
import * as buttons from './ui/buttons';

export default (defaultOptions): void => {
  tinymce.PluginManager.add('ai', function (editor) {
    buttons.register(editor, defaultOptions);
    return {};
  });
};
