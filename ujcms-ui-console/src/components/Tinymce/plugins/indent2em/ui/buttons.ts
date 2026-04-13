import { Editor, Ui } from 'tinymce';

const register = (editor: Editor, defaultOptions: any): void => {
  const onAction = () => editor.execCommand(defaultOptions.id);

  // const onSetup = (buttonApi: Ui.Toolbar.ToolbarToggleButtonInstanceApi) => {
  //   const indentSelector = '*[style*="text-indent"], *[data-mce-style*="text-indent"]';
  //   const containerSelector = 'p,div';
  //   const unbindActiveSelectorChange = editor.selection.selectorChangedWithUnbind(indentSelector, (active: boolean, args: { node: Node; parents: Element[] }) => {
  //     const parent = editor.dom.getParent(args.node, containerSelector);
  //     // 使用 parseInt 可以将 0em 或 0px 转换成 0
  //     buttonApi.setActive(parent != null && parseInt(editor.dom.getStyle(parent, 'text-indent')) > 0 && active);
  //   }).unbind;
  //   const unbindDesabledSelectorChange = editor.selection.selectorChangedWithUnbind(containerSelector, (active: boolean) => {
  //     buttonApi.setDisabled(!active);
  //   }).unbind;
  //   return () => {
  //     unbindActiveSelectorChange();
  //     unbindDesabledSelectorChange();
  //   };
  // };

  // const onSetup = (api: Ui.Toolbar.ToolbarToggleButtonInstanceApi) => {
  //   const { dom } = editor;
  //   const nodeChangeHandler = (e: EditorEvent<Events.NodeChangeEvent>) => {
  //     const { parents } = e;
  //     const parent = parents[parents.length - 1];
  //     const enabled = ['p', 'div'].includes(parent?.nodeName.toLowerCase());
  //     api.setDisabled(!enabled);
  //     // 使用 parseInt 可以将 0em 或 0px 转换成 0
  //     api.setActive(enabled && parseInt(dom.getStyle(parent, 'text-indent')) > 0);
  //   };
  //   editor.on('NodeChange', nodeChangeHandler);
  //   return () => editor.off('NodeChange', nodeChangeHandler);
  // };

  const onSetup = (api: Ui.Toolbar.ToolbarToggleButtonInstanceApi) => {
    const indent2em = [
      {
        selector: 'p,div',
        styles: {
          textIndent: '2em',
        },
        inherit: false,
      },
    ];
    editor.formatter.register(defaultOptions.id, indent2em);
    const nodeChangeHandler = () => {
      api.setActive(editor.formatter.match(defaultOptions.id));
    };
    editor.on('NodeChange', nodeChangeHandler);
    return () => editor.off('NodeChange', nodeChangeHandler);
  };

  if (!editor.ui.registry.getAll().icons[defaultOptions.id]) {
    editor.ui.registry.addIcon(defaultOptions.id, defaultOptions.icon);
  }

  editor.ui.registry.addToggleButton(defaultOptions.id, {
    icon: defaultOptions.id,
    tooltip: defaultOptions.tooltip,
    onAction,
    onSetup,
  });

  editor.ui.registry.addToggleMenuItem(defaultOptions.id, {
    icon: defaultOptions.id,
    text: defaultOptions.tooltip,
    onAction,
  });
};

export { register };
