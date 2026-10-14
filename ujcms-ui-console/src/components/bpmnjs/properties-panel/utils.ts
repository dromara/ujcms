// createElement follows the helper of the same name in bpmn-js-properties-panel (Copyright (c) 2015 camunda Services GmbH, MIT; see NOTICE).
export function createElement(type: string, properties: any, parent: any, bpmnFactory: any) {
  const element = bpmnFactory.create(type, properties);
  if (parent) {
    element.$parent = parent;
  }
  return element;
}
