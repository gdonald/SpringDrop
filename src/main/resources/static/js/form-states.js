// Applies the conditions a form's elements declare: show, require, or disable a
// control based on what another control holds. The server writes each condition
// as a data-state-<condition>="element:value" attribute and enforces the same
// rules on submission, so a browser without this script reaches the same result.

const CONDITIONS = ['visible', 'required', 'disabled'];

function parse(selector) {
  const separator = selector.indexOf(':');
  return {
    dependsOn: selector.slice(0, separator),
    equalsValue: selector.slice(separator + 1),
  };
}

function valueOf(control) {
  if (!control) {
    return '';
  }
  if (control.type === 'checkbox') {
    return control.checked ? 'true' : 'false';
  }
  return control.value;
}

function fieldOf(control) {
  return control.closest('.mb-3') || control;
}

function applyCondition(form, control, condition) {
  const attribute = control.getAttribute(`data-state-${condition}`);
  if (attribute === null) {
    return;
  }
  const { dependsOn, equalsValue } = parse(attribute);
  const holds = valueOf(form.querySelector(`[name="${dependsOn}"]`)) === equalsValue;

  if (condition === 'visible') {
    fieldOf(control).hidden = !holds;
  } else if (condition === 'required') {
    control.required = holds;
  } else {
    control.disabled = holds;
  }
}

export function applyStates(form) {
  const selector = CONDITIONS.map((condition) => `[data-state-${condition}]`).join(',');
  form.querySelectorAll(selector).forEach((control) => {
    CONDITIONS.forEach((condition) => applyCondition(form, control, condition));
  });
}

export function watchStates(form) {
  applyStates(form);
  form.addEventListener('change', () => applyStates(form));
  return form;
}

export function initFormStates(root = document) {
  return Array.from(root.querySelectorAll('[data-form-states]')).map(watchStates);
}
