import { afterEach, describe, expect, it } from 'vitest';
import { applyReplacements, initBigPipe } from '../../main/resources/static/js/big-pipe.js';

function replacement(id, html) {
  const script = document.createElement('script');
  script.type = 'application/json';
  script.setAttribute('data-big-pipe-replacement-for', id);
  script.textContent = JSON.stringify({ html });
  return script;
}

describe('big pipe', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('puts each sent placeholder in the place of its marker', () => {
    document.body.innerHTML = '<p>Shell <span data-big-pipe-placeholder-id="placeholder-1"></span></p>';
    document.body.append(replacement('placeholder-1', '<strong>Signed in as edith</strong>'));

    expect(applyReplacements()).toBe(1);

    expect(document.body.querySelector('p').innerHTML).toBe('Shell <strong>Signed in as edith</strong>');
    expect(document.querySelector('script')).toBeNull();
  });

  it('drops a placeholder whose marker is not on the page', () => {
    document.body.append(replacement('placeholder-9', '<em>stray</em>'));

    applyReplacements();

    expect(document.body.innerHTML).toBe('');
  });

  it('applies placeholders as they arrive until the page is loaded', async () => {
    document.body.innerHTML = '<span data-big-pipe-placeholder-id="placeholder-1"></span>'
      + '<span data-big-pipe-placeholder-id="placeholder-2"></span>';
    const observer = initBigPipe();

    document.body.append(replacement('placeholder-1', '<b>first</b>'));
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(document.body.querySelector('b').textContent).toBe('first');

    document.body.append(replacement('placeholder-2', '<i>second</i>'));
    document.dispatchEvent(new Event('DOMContentLoaded'));
    expect(document.body.querySelector('i').textContent).toBe('second');
    observer.disconnect();
  });

  it('watches a root that is not a whole document', () => {
    const root = document.createElement('div');
    root.innerHTML = '<span data-big-pipe-placeholder-id="placeholder-1"></span>';
    root.append(replacement('placeholder-1', '<u>inside</u>'));

    initBigPipe(root).disconnect();

    expect(root.innerHTML).toBe('<u>inside</u>');
  });
});
