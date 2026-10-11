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

  function addListingForm() {
    document.body.innerHTML = `
      <form id="views-bulk-form" data-bulk-select>
        <button type="submit" data-bulk-apply>Apply to selected items</button>
      </form>
      <table>
        <tr><td><input type="checkbox" name="items" value="node:1" form="views-bulk-form" data-bulk-row></td></tr>
        <tr><td><input type="checkbox" name="other" value="x"></td></tr>
      </table>`;
    return document.querySelector('form');
  }

  function tick(name, checked) {
    const box = document.querySelector(`[name="${name}"]`);
    box.checked = checked;
    box.dispatchEvent(new window.Event('change', { bubbles: true }));
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

    tick('comment_2', true);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(false);

    tick('comment_2', false);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(true);
  });

  it('counts a box outside the form that joins it through its form attribute', () => {
    const form = addListingForm();
    initBulkSelection();

    tick('items', true);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(false);
  });

  it('ignores a change to a box that belongs to no bulk form', () => {
    const form = addListingForm();
    initBulkSelection();

    tick('other', true);
    expect(form.querySelector('[data-bulk-apply]').disabled).toBe(true);
  });
});
