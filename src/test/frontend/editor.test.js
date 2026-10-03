import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  BUTTONS, Editor, buttonsFor, clean, initEditors,
} from '../../main/resources/static/js/editor.js';

const RESTRICTED = ['a', 'em', 'strong', 'cite', 'blockquote', 'code', 'ul', 'ol', 'li', 'dl', 'dt', 'dd',
  'h2', 'h3', 'h4', 'h5', 'h6'];

const BASIC = [...RESTRICTED, 'span', 'img'];

const FORMATS = { basic_html: BASIC, restricted_html: RESTRICTED, plain_text: [] };

function form({ value = '<p>Opening hours</p>', format = 'basic_html', selector = true, token = true } = {}) {
  const options = Object.keys(FORMATS)
    .map((id) => `<option value="${id}"${id === format ? ' selected' : ''}>${id}</option>`).join('');
  document.body.innerHTML = '<form>'
    + (token ? '<input type="hidden" name="_csrf" value="token-1">' : '')
    + '<fieldset>'
    + `<textarea name="body:value" data-editor-target="true" data-editor-formats='${JSON.stringify(FORMATS)}'`
    + ' data-editor-upload="/editor/upload" data-editor-image-extensions="png jpg"'
    + ' data-editor-image-max-filesize="1000"'
    + ' data-editor-image-message-extension="Only files with these extensions are allowed: png jpg."'
    + ` data-editor-image-message-size="The file is larger than 1000 bytes.">${value}</textarea>`
    + (selector ? `<select name="body:format" data-editor-format-selector="true">${options}</select>` : '')
    + '</fieldset></form>';
  return document.querySelector('textarea');
}

function area() {
  return document.querySelector('[data-editor-area]');
}

function buttons() {
  return Array.from(document.querySelectorAll('[data-editor-button]')).map((button) => button.dataset.editorButton);
}

function press(id) {
  document.querySelector(`[data-editor-button="${id}"]`).click();
}

function select(node, start, end) {
  const range = document.createRange();
  range.setStart(node, start);
  range.setEnd(node, end);
  document.getSelection().removeAllRanges();
  document.getSelection().addRange(range);
  return range;
}

function caretIn(node) {
  return select(node, 0, 0);
}

function chooseFormat(format) {
  const selector = document.querySelector('select');
  selector.value = format;
  selector.dispatchEvent(new Event('change'));
}

function answer(ok, body) {
  return Promise.resolve({
    ok,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(body),
  });
}

afterEach(() => {
  document.body.innerHTML = '';
  document.getSelection().removeAllRanges();
});

describe('buttonsFor', () => {
  it('offers exactly the buttons whose tags the format keeps', () => {
    expect(buttonsFor(RESTRICTED).map((button) => button.id)).toEqual(['bold', 'italic', 'code', 'link',
      'heading2', 'heading3', 'heading4', 'heading5', 'heading6', 'quote', 'bulleted', 'numbered']);
    expect(buttonsFor(BASIC).map((button) => button.id)).toContain('image');
    expect(buttonsFor(['ul'])).toEqual([]);
    expect(buttonsFor(BUTTONS.flatMap((button) => button.tags))).toHaveLength(BUTTONS.length);
  });
});

describe('clean', () => {
  it('keeps the tags the format keeps and unwraps the rest with their content', () => {
    expect(clean('<p><strong>Open</strong> <span>daily</span></p><div>from nine</div>', ['strong']))
      .toBe('<p><strong>Open</strong> daily</p>from nine');
  });

  it('drops what could run', () => {
    expect(clean('<p onclick="steal()" style="color:red">Hi</p><script>steal()</script>'
      + '<a href="javascript:steal()">x</a><a href="/node/1">y</a><img src="https://example.com/a.png">'
      + '<img src="data:image/png;base64,AA">', ['a', 'img']))
      .toBe('<p>Hi</p><a>x</a><a href="/node/1">y</a><img src="https://example.com/a.png"><img>');
  });

  it('keeps comments and text as they are', () => {
    expect(clean('<!-- note -->plain', [])).toBe('<!-- note -->plain');
  });
});

describe('initEditors', () => {
  it('puts an editor with the format\'s buttons over the textarea and keeps the textarea for the form', () => {
    const textarea = form();

    initEditors();

    expect(textarea.hidden).toBe(true);
    expect(area().innerHTML).toBe('<p>Opening hours</p>');
    expect(buttons()).toEqual(buttonsFor(BASIC).map((button) => button.id));
  });

  it('follows the format choice, falling back to the textarea for a format keeping no tags', () => {
    const textarea = form();
    initEditors();

    chooseFormat('restricted_html');
    expect(buttons()).not.toContain('image');
    chooseFormat('plain_text');
    expect(area()).toBeNull();
    expect(textarea.hidden).toBe(false);
    chooseFormat('plain_text');
    chooseFormat('basic_html');
    expect(buttons()).toContain('image');
  });

  it('leaves a textarea in a plain text format as it is', () => {
    const textarea = form({ format: 'plain_text' });

    initEditors();

    expect(area()).toBeNull();
    expect(textarea.hidden).toBe(false);
  });

  it('uses the first format when there is no format choice', () => {
    form({ selector: false });

    initEditors();

    expect(buttons()).toContain('image');
  });

  it('copies what is written into the textarea as it is written and when the form is sent', () => {
    const textarea = form();
    initEditors();

    area().innerHTML = '<p>Closed Mondays</p>';
    area().dispatchEvent(new Event('input'));
    expect(textarea.value).toBe('<p>Closed Mondays</p>');
    area().innerHTML = '<p>Closed Tuesdays</p>';
    textarea.form.dispatchEvent(new Event('submit'));
    expect(textarea.value).toBe('<p>Closed Tuesdays</p>');
  });

  it('works on a textarea outside a form and a fieldset', () => {
    document.body.innerHTML = `<div><textarea data-editor-target="true" data-editor-formats='${JSON.stringify({ basic_html: BASIC })}'>`
      + '<p>Hi</p></textarea></div>';

    expect(initEditors()).toHaveLength(1);
    expect(area().innerHTML).toBe('<p>Hi</p>');
  });

  it('treats a textarea without formats as plain text', () => {
    document.body.innerHTML = '<div><textarea data-editor-target="true">Hi</textarea></div>';

    initEditors();

    expect(area()).toBeNull();
  });
});

describe('Editor commands', () => {
  function editorWith(value) {
    form({ value });
    return initEditors()[0];
  }

  it('wraps the selection in the inline tag of each inline button', () => {
    for (const [id, tag] of [['bold', 'strong'], ['italic', 'em'], ['code', 'code']]) {
      editorWith('<p>Open daily</p>');
      select(area().querySelector('p').firstChild, 0, 4);

      press(id);

      expect(area().innerHTML).toBe(`<p><${tag}>Open</${tag}> daily</p>`);
      expect(document.querySelector('textarea').value).toBe(area().innerHTML);
    }
  });

  it('wraps for the inline tags restricted formats leave out', () => {
    const editor = editorWith('<p>H2O</p>');
    for (const button of BUTTONS.filter((each) => ['underline', 'strikethrough', 'subscript', 'superscript']
      .includes(each.id))) {
      area().innerHTML = '<p>H2O</p>';
      select(area().querySelector('p').firstChild, 1, 2);
      button.run(editor);
      expect(area().querySelector('p').children[0].tagName).toBe(button.tags[0].toUpperCase());
    }
  });

  it('does nothing without a selection, with an empty one, or with one outside the editor', () => {
    editorWith('<p>Open daily</p>');

    document.getSelection().removeAllRanges();
    press('bold');
    caretIn(area().querySelector('p').firstChild);
    press('bold');
    const outside = document.createElement('p');
    outside.textContent = 'elsewhere';
    document.body.appendChild(outside);
    select(outside.firstChild, 0, 4);
    press('bold');
    press('heading2');
    press('bulleted');

    expect(area().innerHTML).toBe('<p>Open daily</p>');
  });

  it('links the selection to a web, mail, or same-site address and to nothing else', () => {
    form({ value: '<p>Visit us</p>' });
    const answers = ['javascript:alert(1)', '/contact', null];
    const editor = initEditors(document, { prompt: () => answers.shift() })[0];

    select(area().querySelector('p').firstChild, 0, 5);
    press('link');
    expect(area().querySelector('a')).toBeNull();
    press('link');
    expect(area().querySelector('a').getAttribute('href')).toBe('/contact');
    select(area().querySelector('p').lastChild, 0, 3);
    editor.link();
    expect(area().querySelectorAll('a')).toHaveLength(1);
  });

  it('turns a block into a heading or quote and back into a paragraph', () => {
    editorWith('<p>Opening hours</p><p>Daily</p>');

    caretIn(area().querySelector('p').firstChild);
    press('heading2');
    expect(area().innerHTML).toBe('<h2>Opening hours</h2><p>Daily</p>');
    caretIn(area().querySelector('h2').firstChild);
    press('heading2');
    expect(area().innerHTML).toBe('<p>Opening hours</p><p>Daily</p>');
    caretIn(area().lastChild.firstChild);
    press('quote');
    expect(area().lastChild.tagName).toBe('BLOCKQUOTE');
  });

  it('gathers text sitting straight in the editor into a paragraph before changing it', () => {
    editorWith('Loose text');

    caretIn(area().firstChild);
    press('heading3');

    expect(area().innerHTML).toBe('<h3>Loose text</h3>');
  });

  it('finds the block from a caret placed between blocks', () => {
    editorWith('<p>One</p><p>Two</p>');

    select(area(), 1, 1);
    press('heading4');
    expect(area().innerHTML).toBe('<p>One</p><h4>Two</h4>');
    select(area(), 2, 2);
    press('heading4');
    expect(area().innerHTML).toBe('<p>One</p><p>Two</p>');
  });

  it('finds no block in an empty editor', () => {
    editorWith('');

    select(area(), 0, 0);
    press('heading5');
    press('numbered');

    expect(area().innerHTML).toBe('');
  });

  it('makes a block a list item and a list back into paragraphs', () => {
    editorWith('<p>Tea</p>');

    caretIn(area().querySelector('p').firstChild);
    press('bulleted');
    expect(area().innerHTML).toBe('<ul><li>Tea</li></ul>');
    caretIn(area().querySelector('li').firstChild);
    press('numbered');
    expect(area().innerHTML).toBe('<ol><li>Tea</li></ol>');
    area().innerHTML = '<ul><li>Tea</li><li>Coffee</li></ul>';
    caretIn(area().querySelector('li').firstChild);
    press('bulleted');
    expect(area().innerHTML).toBe('<p>Tea</p><p>Coffee</p>');
  });

  it('offers the headings restricted formats keep', () => {
    editorWith('<p>Title</p>');
    for (const level of [5, 6]) {
      caretIn(area().firstChild.firstChild);
      press(`heading${level}`);
      expect(area().firstChild.tagName).toBe(`H${level}`);
      press(`heading${level}`);
    }
  });
});

describe('Editor images', () => {
  function imageEditor(fetch, { token = true, prompt = () => 'The main hall' } = {}) {
    form({ value: '<p>Hall</p>', token });
    return initEditors(document, { fetch, prompt })[0];
  }

  function picked(name, size = 10) {
    return new File([new Uint8Array(size)], name);
  }

  it('picks an image through a file input taking the allowed kinds', () => {
    const editor = imageEditor(vi.fn());
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});
    const upload = vi.spyOn(editor, 'uploadImage').mockResolvedValue();

    press('image');
    const input = click.mock.contexts[0];
    Object.defineProperty(input, 'files', { value: [picked('hall.png')] });
    input.dispatchEvent(new Event('change'));

    expect(input.accept).toBe('.png,.jpg');
    expect(upload).toHaveBeenCalledWith(input.files[0]);
    click.mockRestore();
  });

  it('inserts the uploaded image where the caret is, with its size, alternative text, and file', async () => {
    const fetch = vi.fn(() => answer(true, { id: 7, url: '/files/2026-10/hall.png', width: 40, height: 30 }));
    const editor = imageEditor(fetch);
    select(area().querySelector('p').firstChild, 4, 4);

    await editor.uploadImage(picked('hall.png'));

    const image = area().querySelector('p img');
    expect([image.getAttribute('src'), image.getAttribute('alt'), image.getAttribute('width'),
      image.getAttribute('height'), image.getAttribute('data-file-id')])
      .toEqual(['/files/2026-10/hall.png', 'The main hall', '40', '30', '7']);
    const [url, init] = fetch.mock.calls[0];
    expect(url).toBe('/editor/upload');
    expect([init.body.get('format'), init.body.get('_csrf'), init.body.get('file').name])
      .toEqual(['basic_html', 'token-1', 'hall.png']);
    expect(document.querySelector('textarea').value).toContain('data-file-id="7"');
  });

  it('adds the image at the end without a caret and without alternative text when none is given', async () => {
    const editor = imageEditor(() => answer(true, { id: 8, url: '/files/a.png', width: 1, height: 1 }),
      { token: false, prompt: () => null });
    document.getSelection().removeAllRanges();

    await editor.uploadImage(picked('a.png'));

    expect(area().lastChild.tagName).toBe('IMG');
    expect(area().lastChild.getAttribute('alt')).toBe('');
  });

  it('refuses an image of another kind or too large with the server\'s message, without sending it', async () => {
    const fetch = vi.fn();
    const editor = imageEditor(fetch);

    await editor.uploadImage(picked('hall.gif'));
    expect(editor.message.textContent).toBe('Only files with these extensions are allowed: png jpg.');
    await editor.uploadImage(picked('hall.png', 1001));
    expect(editor.message.textContent).toBe('The file is larger than 1000 bytes.');
    await editor.uploadImage(undefined);

    expect(fetch).not.toHaveBeenCalled();
    expect(editor.message.hidden).toBe(false);
  });

  it('shows why the server refused the image, or that the upload failed', async () => {
    const answers = [answer(false, 'The file is not an image.'), answer(false, '')];
    const editor = imageEditor(() => answers.shift());

    await editor.uploadImage(picked('hall.png'));
    expect(editor.message.textContent).toBe('The file is not an image.');
    await editor.uploadImage(picked('hall.png'));
    expect(editor.message.textContent).toBe('The image could not be uploaded.');
    await editor.uploadImage(picked('README'));
    expect(editor.message.textContent).toBe('Only files with these extensions are allowed: png jpg.');
  });

  it('reports a request that does not get through', async () => {
    const editor = imageEditor(() => Promise.reject(new Error('down')));

    await editor.uploadImage(picked('hall.png'));

    expect(editor.message.textContent).toBe('The image could not be uploaded.');
    expect(area().querySelector('img')).toBeNull();
  });

  it('sends the first format without a format choice and takes any image without limits', async () => {
    document.body.innerHTML = `<form><textarea data-editor-target="true" data-editor-upload="/editor/upload"
      data-editor-formats='${JSON.stringify({ basic_html: BASIC })}'></textarea></form>`;
    const fetch = vi.fn(() => answer(true, { id: 9, url: '/files/b.png', width: 2, height: 2 }));
    const editor = initEditors(document, { fetch, prompt: () => '' })[0];

    await editor.uploadImage(picked('notes.webp', 50_000));

    expect(fetch.mock.calls[0][1].body.get('format')).toBe('basic_html');
  });

  it('uses the browser\'s prompt and fetch when none are given', async () => {
    form();
    const editor = new Editor(document.querySelector('textarea'));
    vi.stubGlobal('prompt', () => 'Garden');
    vi.stubGlobal('fetch', () => answer(true, { id: 3, url: '/files/c.png', width: 3, height: 3 }));
    editor.follow();

    await editor.uploadImage(picked('c.png'));

    expect(area().querySelector('img').getAttribute('alt')).toBe('Garden');
    vi.unstubAllGlobals();
  });
});
