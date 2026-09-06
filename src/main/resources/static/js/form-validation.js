// Checks a form before it is sent, using the rules the server wrote onto each
// control as data-rule-<type> attributes. Every message comes from the server
// too, in data-rule-<type>-message, so the browser never invents wording of its
// own and the two sides cannot disagree.

const RULES = ['required', 'maxlength', 'pattern', 'email', 'range'];

const EMAIL_ADDRESS = /[^@\s]+@[^@\s.]+(\.[^@\s.]+)+/;

function satisfied(type, parameter, value) {
  if (type === 'required') {
    return value.trim() !== '';
  }
  if (value.trim() === '') {
    return true;
  }
  if (type === 'maxlength') {
    return value.length <= Number(parameter);
  }
  if (type === 'pattern') {
    return new RegExp(`^(?:${parameter})$`).test(value);
  }
  if (type === 'email') {
    return EMAIL_ADDRESS.test(value);
  }
  const [min, max] = parameter.split(':');
  const number = Number(value);
  return (min === '' || number >= Number(min)) && (max === '' || number <= Number(max));
}

function feedbackFor(control) {
  const field = control.closest('.mb-3') || control.parentElement;
  let feedback = field.querySelector('.invalid-feedback');
  if (!feedback) {
    feedback = document.createElement('div');
    feedback.className = 'invalid-feedback d-block';
    field.appendChild(feedback);
  }
  return feedback;
}

function clear(control) {
  control.classList.remove('is-invalid');
  const field = control.closest('.mb-3') || control.parentElement;
  const feedback = field.querySelector('.invalid-feedback');
  if (feedback) {
    feedback.remove();
  }
}

function firstViolation(control) {
  for (const type of RULES) {
    const parameter = control.getAttribute(`data-rule-${type}`);
    if (parameter !== null && !satisfied(type, parameter, control.value)) {
      return control.getAttribute(`data-rule-${type}-message`);
    }
  }
  return null;
}

export function checkControl(control) {
  clear(control);
  const message = firstViolation(control);
  if (message === null) {
    return true;
  }
  control.classList.add('is-invalid');
  feedbackFor(control).textContent = message;
  return false;
}

export function checkForm(form) {
  const selector = RULES.map((type) => `[data-rule-${type}]`).join(',');
  return Array.from(form.querySelectorAll(selector))
    .map((control) => checkControl(control))
    .every((passed) => passed);
}

export function watchValidation(form) {
  form.addEventListener('submit', (event) => {
    if (!checkForm(form)) {
      event.preventDefault();
    }
  });
  return form;
}

export function initFormValidation(root = document) {
  return Array.from(root.querySelectorAll('[data-form-states]')).map(watchValidation);
}
