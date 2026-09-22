import { afterEach, describe, expect, it } from 'vitest';
import { initDragOrder } from '../../main/resources/static/js/drag-order.js';

describe('initDragOrder', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  function table(rows) {
    document.body.innerHTML = `<table data-drag-order="menu-links"><tbody>${rows}</tbody></table>`;
    return document.querySelector('table');
  }

  function storedRow(name, weight) {
    return `<tr data-drag-handle="true" data-name="${name}">`
      + `<td><input type="number" data-drag-weight value="${weight}"></td></tr>`;
  }

  function moduleRow(name) {
    return `<tr data-name="${name}"><td>fixed</td></tr>`;
  }

  function rowNamed(name) {
    return document.querySelector(`tr[data-name="${name}"]`);
  }

  function order() {
    return Array.from(document.querySelectorAll('tbody tr')).map((row) => row.dataset.name);
  }

  function weights() {
    return Array.from(document.querySelectorAll('[data-drag-weight]')).map((input) => input.value);
  }

  function drag(from, to) {
    rowNamed(from).dispatchEvent(new window.Event('dragstart'));
    const over = new window.Event('dragover', { cancelable: true });
    rowNamed(to).dispatchEvent(over);
    rowNamed(to).dispatchEvent(new window.Event('drop', { cancelable: true }));
    return over;
  }

  it('enhances every table asking to be ordered by dragging', () => {
    table(storedRow('first', 0));
    expect(initDragOrder()).toBe(1);
  });

  it('drops a row above the one it was dragged onto', () => {
    table(storedRow('first', 0) + storedRow('second', 1) + storedRow('third', 2));
    initDragOrder();

    drag('third', 'first');

    expect(order()).toEqual(['third', 'first', 'second']);
  });

  it('drops a row below the one it was dragged onto when dragged downwards', () => {
    table(storedRow('first', 0) + storedRow('second', 1) + storedRow('third', 2));
    initDragOrder();

    drag('first', 'third');

    expect(order()).toEqual(['second', 'third', 'first']);
  });

  it('renumbers the weights to the order the rows now come in', () => {
    table(storedRow('first', 7) + storedRow('second', 3));
    initDragOrder();

    drag('second', 'first');

    expect(weights()).toEqual(['0', '1']);
  });

  it('leaves a row without a weight input alone while renumbering', () => {
    table(storedRow('first', 5) + moduleRow('fixed') + storedRow('third', 6));
    initDragOrder();

    drag('third', 'first');

    expect(order()).toEqual(['third', 'first', 'fixed']);
    expect(weights()).toEqual(['0', '1']);
  });

  it('accepts the drag so the browser allows the drop', () => {
    table(storedRow('first', 0) + storedRow('second', 1));
    initDragOrder();

    expect(drag('first', 'second').defaultPrevented).toBe(true);
  });

  it('leaves the order alone when a row is dropped onto itself', () => {
    table(storedRow('first', 4) + storedRow('second', 9));
    initDragOrder();

    drag('first', 'first');

    expect(order()).toEqual(['first', 'second']);
    expect(weights()).toEqual(['4', '9']);
  });

  it('ignores a drop with nothing being dragged', () => {
    table(storedRow('first', 4) + storedRow('second', 9));
    initDragOrder();

    rowNamed('second').dispatchEvent(new window.Event('drop', { cancelable: true }));

    expect(order()).toEqual(['first', 'second']);
    expect(weights()).toEqual(['4', '9']);
  });

  it('does not make a module-provided row draggable', () => {
    table(storedRow('first', 4) + moduleRow('fixed'));
    initDragOrder();

    rowNamed('fixed').dispatchEvent(new window.Event('dragstart'));
    rowNamed('first').dispatchEvent(new window.Event('drop', { cancelable: true }));

    expect(order()).toEqual(['first', 'fixed']);
  });
});
