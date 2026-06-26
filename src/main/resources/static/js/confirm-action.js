// Progressive enhancement: intercept clicks on elements carrying a
// data-confirm message and cancel the action when the user declines.
export function initConfirm(root = document) {
  const elements = root.querySelectorAll('[data-confirm]');
  elements.forEach((element) => {
    element.addEventListener('click', (event) => {
      const message = element.getAttribute('data-confirm');
      if (!window.confirm(message)) {
        event.preventDefault();
      }
    });
  });
  return elements.length;
}
