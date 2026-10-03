// Puts each placeholder a streamed page sends after its shell in the place
// of its marker, as the data script holding it arrives.
const REPLACEMENT = 'script[data-big-pipe-replacement-for]';

function placeholderFor(id, root) {
  return Array.from(root.querySelectorAll('[data-big-pipe-placeholder-id]'))
    .find((element) => element.getAttribute('data-big-pipe-placeholder-id') === id);
}

export function applyReplacements(root = document) {
  const scripts = root.querySelectorAll(REPLACEMENT);
  scripts.forEach((script) => {
    const placeholder = placeholderFor(script.getAttribute('data-big-pipe-replacement-for'), root);
    script.remove();
    if (placeholder) {
      const template = document.createElement('template');
      template.innerHTML = JSON.parse(script.textContent).html;
      placeholder.replaceWith(template.content);
    }
  });
  return scripts.length;
}

export function initBigPipe(root = document) {
  applyReplacements(root);
  const observer = new MutationObserver(() => applyReplacements(root));
  observer.observe(root.documentElement || root, { childList: true, subtree: true });
  root.addEventListener('DOMContentLoaded', () => {
    applyReplacements(root);
    observer.disconnect();
  });
  return observer;
}
