export function createElement(type: string, properties: any, parent: any, bpmnFactory: any) {
  const element = bpmnFactory.create(type, properties);
  if (parent) {
    element.$parent = parent;
  }
  return element;
}
