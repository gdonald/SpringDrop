import { afterEach, describe, expect, it } from 'vitest';
import {
  checkControl,
  checkForm,
  initFormValidation,
  watchValidation,
} from '../../main/resources/static/js/form-validation.js';

// The messages here are the ones the server writes into the data attributes, so
// a rule reads the same either side. The backend asserts the same wording in
// FormValidationRuleTest.
const TOO_LONG = 'This value is too long. It must be at most 10 characters.';
const NOT_EMAIL = 'This value is not an email address.';
const REQUIRED = 'This value is required.';
const OUT_OF_RANGE = 'This value should be between 1 and 10.';
const WRONG_FORMAT = 'This value is not in the expected format.';

describe('form validation', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  // Mirrors what FormRenderer emits: the control inside a .mb-3 wrapper, with
  // the rule and its message as data attributes.
  function addField(rules, value = '') {
    const attributes = Object.entries(rules)
      .map(([type, rule]) => `data-rule-${type}="${rule.parameter}" data-rule-${type}-message="${rule.message}"`)
      .join(' ');
    document.body.innerHTML = `
      <form data-form-states>
        <div class="mb-3">
          <input type="text" name="value" id="value" value="${value}" ${attributes}>
        </div>
        <button type="submit">Save</button>
      </form>`;
    return {
      form: document.querySelector('form'),
      control: document.querySelector('#value'),
    };
  }

  function feedback() {
    return document.querySelector('.invalid-feedback');
  }

  it('accepts a value within its maximum length', () => {
    const { control } = addField({ maxlength: { parameter: '10', message: TOO_LONG } }, 'short');

    expect(checkControl(control)).toBe(true);
    expect(feedback()).toBeNull();
  });

  it('shows the maximum length message the server wrote', () => {
    const { control } = addField(
      { maxlength: { parameter: '10', message: TOO_LONG } },
      'far too long to fit',
    );

    expect(checkControl(control)).toBe(false);
    expect(control.classList.contains('is-invalid')).toBe(true);
    expect(feedback().textContent).toBe(TOO_LONG);
  });

  it('reports a missing required value', () => {
    const { control } = addField({ required: { parameter: '', message: REQUIRED } });

    expect(checkControl(control)).toBe(false);
    expect(feedback().textContent).toBe(REQUIRED);
  });

  it('accepts a filled in required value', () => {
    const { control } = addField({ required: { parameter: '', message: REQUIRED } }, 'here');

    expect(checkControl(control)).toBe(true);
  });

  it('leaves an empty optional value alone', () => {
    const { control } = addField({ maxlength: { parameter: '10', message: TOO_LONG } }, '  ');

    expect(checkControl(control)).toBe(true);
  });

  it('reports an address that is not an email address', () => {
    const { control } = addField({ email: { parameter: '', message: NOT_EMAIL } }, 'alice at example');

    expect(checkControl(control)).toBe(false);
    expect(feedback().textContent).toBe(NOT_EMAIL);
  });

  it('accepts an email address', () => {
    const { control } = addField({ email: { parameter: '', message: NOT_EMAIL } }, 'alice@example.com');

    expect(checkControl(control)).toBe(true);
  });

  it('reports a value that does not match the pattern', () => {
    const { control } = addField({ pattern: { parameter: '[a-z_]+', message: WRONG_FORMAT } }, 'Not This');

    expect(checkControl(control)).toBe(false);
    expect(feedback().textContent).toBe(WRONG_FORMAT);
  });

  it('accepts a value matching the pattern', () => {
    const { control } = addField({ pattern: { parameter: '[a-z_]+', message: WRONG_FORMAT } }, 'machine_name');

    expect(checkControl(control)).toBe(true);
  });

  it('reports a number under its minimum and over its maximum', () => {
    const under = addField({ range: { parameter: '1:10', message: OUT_OF_RANGE } }, '0');
    expect(checkControl(under.control)).toBe(false);
    expect(feedback().textContent).toBe(OUT_OF_RANGE);

    const over = addField({ range: { parameter: '1:10', message: OUT_OF_RANGE } }, '11');
    expect(checkControl(over.control)).toBe(false);
  });

  it('accepts a number within an open ended range', () => {
    const openTop = addField({ range: { parameter: '1:', message: OUT_OF_RANGE } }, '9000');
    expect(checkControl(openTop.control)).toBe(true);

    const openBottom = addField({ range: { parameter: ':10', message: OUT_OF_RANGE } }, '-5');
    expect(checkControl(openBottom.control)).toBe(true);
  });

  it('replaces an earlier message rather than stacking them up', () => {
    const { control } = addField({ maxlength: { parameter: '10', message: TOO_LONG } }, 'far too long to fit');
    checkControl(control);
    checkControl(control);

    expect(document.querySelectorAll('.invalid-feedback')).toHaveLength(1);
  });

  it('falls back to the parent when the control is not in a field wrapper', () => {
    document.body.innerHTML = `
      <form data-form-states>
        <span><input type="text" id="bare" value="" data-rule-required="" data-rule-required-message="${REQUIRED}"></span>
      </form>`;
    const control = document.querySelector('#bare');

    expect(checkControl(control)).toBe(false);
    expect(feedback().textContent).toBe(REQUIRED);
  });

  it('checks every control of the form at once', () => {
    const { form } = addField({ maxlength: { parameter: '10', message: TOO_LONG } }, 'far too long to fit');

    expect(checkForm(form)).toBe(false);
  });

  it('stops a submission that would fail', () => {
    const { form } = addField({ required: { parameter: '', message: REQUIRED } });
    watchValidation(form);

    const event = new window.Event('submit', { cancelable: true });
    form.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
  });

  it('lets a valid submission through', () => {
    const { form } = addField({ required: { parameter: '', message: REQUIRED } }, 'here');
    watchValidation(form);

    const event = new window.Event('submit', { cancelable: true });
    form.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(false);
  });

  it('binds every form on the page', () => {
    addField({ required: { parameter: '', message: REQUIRED } });

    expect(initFormValidation()).toHaveLength(1);
  });
});
