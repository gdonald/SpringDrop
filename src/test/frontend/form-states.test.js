import { afterEach, describe, expect, it } from 'vitest';
import { applyStates, initFormStates, watchStates } from '../../main/resources/static/js/form-states.js';

describe('form states', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  // The markup mirrors what FormRenderer emits: each field wrapped in .mb-3,
  // with the condition on the control as a data-state attribute.
  function addForm(conditionAttribute, kindControl = '<input type="text" name="kind" value="">') {
    document.body.innerHTML = `
      <form data-form-states>
        <div class="mb-3">${kindControl}</div>
        <div class="mb-3">
          <input type="text" name="other" id="other" ${conditionAttribute}>
        </div>
      </form>`;
    return {
      form: document.querySelector('form'),
      kind: document.querySelector('[name="kind"]'),
      other: document.querySelector('#other'),
    };
  }

  it('binds every form that declares states', () => {
    addForm('data-state-visible="kind:other"');

    expect(initFormStates()).toHaveLength(1);
  });

  it('hides a field whose condition does not hold', () => {
    const { form, other } = addForm('data-state-visible="kind:other"');

    applyStates(form);

    expect(other.closest('.mb-3').hidden).toBe(true);
  });

  it('shows the field once the other control holds the value', () => {
    const { form, kind, other } = addForm('data-state-visible="kind:other"');
    watchStates(form);

    kind.value = 'other';
    kind.dispatchEvent(new window.Event('change', { bubbles: true }));

    expect(other.closest('.mb-3').hidden).toBe(false);
  });

  it('requires a field only while its condition holds', () => {
    const { form, kind, other } = addForm('data-state-required="kind:other"');
    watchStates(form);
    expect(other.required).toBe(false);

    kind.value = 'other';
    kind.dispatchEvent(new window.Event('change', { bubbles: true }));

    expect(other.required).toBe(true);
  });

  it('disables a field while its condition holds', () => {
    const { form, kind, other } = addForm('data-state-disabled="kind:locked"');
    watchStates(form);

    kind.value = 'locked';
    kind.dispatchEvent(new window.Event('change', { bubbles: true }));

    expect(other.disabled).toBe(true);
  });

  it('reads a checkbox as true or false', () => {
    const { form, kind, other } = addForm(
      'data-state-visible="kind:true"',
      '<input type="checkbox" name="kind">',
    );
    watchStates(form);
    expect(other.closest('.mb-3').hidden).toBe(true);

    kind.checked = true;
    kind.dispatchEvent(new window.Event('change', { bubbles: true }));

    expect(other.closest('.mb-3').hidden).toBe(false);
  });

  it('treats a condition on a control that is not there as unmet', () => {
    const { form, other } = addForm('data-state-visible="missing:yes"');

    applyStates(form);

    expect(other.closest('.mb-3').hidden).toBe(true);
  });

  it('leaves a control with a condition it does not carry alone', () => {
    const { form, other } = addForm('data-state-required="kind:other"');

    applyStates(form);

    expect(other.closest('.mb-3').hidden).toBe(false);
  });

  it('falls back to the control itself when it is not inside a field wrapper', () => {
    document.body.innerHTML = `
      <form data-form-states>
        <input type="text" name="kind" value="">
        <input type="text" id="bare" data-state-visible="kind:other">
      </form>`;
    const form = document.querySelector('form');

    applyStates(form);

    expect(document.querySelector('#bare').hidden).toBe(true);
  });
});
