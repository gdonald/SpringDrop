import { afterEach, describe, expect, it } from 'vitest';
import { initBulkSelection } from '../../main/resources/static/js/bulk-selection.js';

describe('initBulkSelection', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  function addForm() {
    document.body.innerHTML = `
      <form data-bulk-select>
        <input type="checkbox" name="comment_1" data-bulk-row>
        <input type="checkbox" name="comment_2" data-bulk-row>
        <button type="submit" data-bulk-apply>Apply to selected</button>
      </form>`;
    return document.querySelector('form');
  }

  function tick(form, name, checked) {
    const box = form.querySelector(`[name="${name}"]`);
    box.checked = checked;
    form.dispatchEvent(new window.Event('change', { bubbles: true }));
  }

  it('binds only forms marked for bulk selection', () => {
    addForm();
    expect(initBulkSelection()).toBe(1);
  });

  it('keeps the apply button disabled while no row is ticked', () => {
    const form = addForm();
    initBulkSelection();
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(true);
  });

  it('enables the apply button once a row is ticked and disables it again when none is', () => {
    const form = addForm();
    initBulkSelection();

    tick(form, 'comment_2', true);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(false);

    tick(form, 'comment_2', false);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(true);
  });
});
