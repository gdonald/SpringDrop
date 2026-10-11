// Progressive enhancement over a bulk-operations form: keep its apply button
// disabled until at least one row is ticked. The server refuses an empty
// selection the same way, so a browser without this script gets the same answer
// after submitting. A row's box may sit outside the form and join it through
// its form attribute, as a listing's boxes do, so the boxes are the form's
// elements rather than its descendants.

function rowBoxesOf(form) {
  return Array.from(form.elements).filter((element) => element.matches('[data-bulk-row]'));
}

function update(form) {
  const anyTicked = rowBoxesOf(form).some((box) => box.checked);
  form.querySelectorAll('[data-bulk-apply]').forEach((button) => {
    button.disabled = !anyTicked;
  });
}

export function initBulkSelection(root = document) {
  const forms = Array.from(root.querySelectorAll('[data-bulk-select]'));
  forms.forEach((form) => {
    update(form);
    root.addEventListener('change', (event) => {
      if (event.target.form === form) {
        update(form);
      }
    });
  });
  return forms.length;
}
