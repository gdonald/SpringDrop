// Progressive enhancement over the weight inputs: let rows be dragged into the
// order they should come in, and renumber the weights to match. With JS off the
// weight inputs are still there and still submit, so the ordering works either
// way.

function rowsOf(table) {
  return Array.from(table.querySelectorAll('tbody tr'));
}

function renumber(table) {
  rowsOf(table).forEach((row, position) => {
    const weight = row.querySelector('[data-drag-weight]');
    if (weight) {
      weight.value = position;
    }
  });
}

function moveBefore(dragged, target) {
  const following = rowsOf(target.parentNode).indexOf(dragged)
    > rowsOf(target.parentNode).indexOf(target);
  target.parentNode.insertBefore(dragged, following ? target : target.nextSibling);
}

export function initDragOrder(root = document) {
  const tables = root.querySelectorAll('[data-drag-order]');
  tables.forEach((table) => {
    let dragged = null;

    rowsOf(table).forEach((row) => {
      if (!row.hasAttribute('data-drag-handle')) {
        return;
      }
      row.addEventListener('dragstart', () => {
        dragged = row;
      });
      row.addEventListener('dragover', (event) => {
        event.preventDefault();
      });
      row.addEventListener('drop', (event) => {
        event.preventDefault();
        if (dragged && dragged !== row) {
          moveBefore(dragged, row);
          renumber(table);
        }
        dragged = null;
      });
    });
  });
  return tables.length;
}
