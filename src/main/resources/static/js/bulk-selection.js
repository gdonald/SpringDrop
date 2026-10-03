// Progressive enhancement over a bulk-operations form: keep its apply button
// disabled until at least one row is ticked. The server refuses an empty
// selection the same way, so a browser without this script gets the same answer
// after submitting.

function rowBoxesOf(form) {
  return Array.from(form.querySelectorAll('[data-bulk-row]'));
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
    form.addEventListener('change', () => update(form));
  });
  return forms.length;
}
