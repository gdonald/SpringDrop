import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  extensionOf,
  initFileUpload,
  readImageSize,
  refusalFor,
  send,
  storedName,
  updateCapacity,
  uploadPicked,
} from '../../main/resources/static/js/file-upload.js';

const DANGEROUS = 'html js php svg';

class FakeRequest extends EventTarget {
  static answers = [];

  static sent = [];

  static progressShown = [];

  constructor() {
    super();
    this.upload = new EventTarget();
    const answer = FakeRequest.answers.shift() || { status: 200, body: '' };
    this.answer = answer;
  }

  open(method, url) {
    this.method = method;
    this.url = url;
  }

  send(body) {
    this.body = body;
    FakeRequest.sent.push(this);
    queueMicrotask(() => {
      if (this.answer.progress) {
        this.answer.progress.forEach((event) => {
          this.upload.dispatchEvent(event);
          FakeRequest.progressShown.push(document.querySelector('progress').value);
        });
      }
      if (this.answer.fails) {
        this.dispatchEvent(new Event('error'));
        return;
      }
      this.status = this.answer.status;
      this.responseText = this.answer.body;
      this.dispatchEvent(new Event('load'));
    });
  }
}

function item(delta, { preview = true, remove = true } = {}) {
  return `<div data-file-item="${delta}" draggable="true" data-name="file-${delta}">`
    + `<input type="hidden" name="photo[${delta}]:target_id" value="${delta + 10}">`
    + (preview ? `<p data-file-preview="/files/hall-${delta}.png">hall-${delta}.png</p>` : `<p>plan-${delta}.txt</p>`)
    + `<input type="number" data-file-weight value="${delta}">`
    + (remove ? '<input type="checkbox" data-file-remove>' : '')
    + '</div>';
}

function inputAttributes(overrides = {}) {
  const attributes = {
    'data-file-upload': 'true',
    'data-upload-url': '/file/upload',
    'data-entity-type': 'node',
    'data-bundle': 'article',
    'data-field': 'photo',
    'data-file-extensions': 'png jpg',
    'data-dangerous-extensions': DANGEROUS,
    'data-max-filesize': '1000',
    'data-cardinality': '-1',
    'data-message-extension': 'Only files with these extensions are allowed: png jpg.',
    'data-message-size': 'The file is larger than 1000 bytes.',
    'data-image': 'true',
    'data-min-resolution': '20x20',
    'data-max-resolution': '400x400',
    'data-message-not-image': 'The file is not an image.',
    'data-message-too-small': 'The image is smaller than 20x20 pixels.',
    'data-message-too-large': 'The image is larger than 400x400 pixels.',
    ...overrides,
  };
  return Object.entries(attributes)
    .filter(([, value]) => value !== null)
    .map(([name, value]) => `${name}="${value}"`)
    .join(' ');
}

function form({ items = '', attributes = {}, csrf = true, wrapped = true, inForm = true } = {}) {
  const input = `<input type="file" name="photo:upload" ${inputAttributes(attributes)}>`;
  const fieldset = '<fieldset>'
    + `<div data-file-items>${items}</div>`
    + (wrapped ? `<div class="mb-3">${input}</div>` : input)
    + '</fieldset>';
  const token = csrf ? '<input type="hidden" name="_csrf" value="token-1">' : '';
  document.body.innerHTML = inForm ? `<form>${token}${fieldset}</form>` : fieldset;
  return document.querySelector('[data-file-upload]');
}

function picked(name, size = 10) {
  return new File([new Uint8Array(size)], name);
}

function choose(input, files) {
  Object.defineProperty(input, 'files', { value: files, configurable: true });
}

function order() {
  return Array.from(document.querySelectorAll('[data-file-item]')).map((each) => each.dataset.name);
}

function weights() {
  return Array.from(document.querySelectorAll('[data-file-weight]')).map((weight) => weight.value);
}

const sized = (width, height) => () => Promise.resolve({ width, height });

describe('storedName', () => {
  it('keeps the name the server stores the file under', () => {
    expect(storedName('../photos/Main hall (2).PNG', [])).toBe('Main_hall_2_.PNG');
    expect(storedName('C:\\Users\\edith\\plan.pdf', [])).toBe('plan.pdf');
    expect(storedName('...', [])).toBe('file');
  });

  it('adds .txt to a name a server might run or a browser render as a page', () => {
    expect(storedName('page.HTML', ['html'])).toBe('page.HTML.txt');
  });
});

describe('extensionOf', () => {
  it('reads the extension in lower case, or nothing without a dot', () => {
    expect(extensionOf('Hall.JPG')).toBe('jpg');
    expect(extensionOf('README')).toBe('');
  });
});

describe('refusalFor', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('refuses a file whose stored extension is not allowed', async () => {
    const input = form({ attributes: { 'data-file-extensions': 'php png' } });

    expect(await refusalFor(picked('shell.php'), input, sized(50, 50)))
      .toBe('Only files with these extensions are allowed: png jpg.');
  });

  it('refuses a file larger than the limit', async () => {
    const input = form();

    expect(await refusalFor(picked('hall.png', 1001), input, sized(50, 50)))
      .toBe('The file is larger than 1000 bytes.');
  });

  it('refuses a file the browser cannot show as an image', async () => {
    const input = form();

    expect(await refusalFor(picked('hall.png'), input, () => Promise.resolve(null)))
      .toBe('The file is not an image.');
  });

  it('refuses an image outside the pixel bounds', async () => {
    const input = form();

    expect(await refusalFor(picked('hall.png'), input, sized(10, 50))).toBe('The image is smaller than 20x20 pixels.');
    expect(await refusalFor(picked('hall.png'), input, sized(50, 10))).toBe('The image is smaller than 20x20 pixels.');
    expect(await refusalFor(picked('hall.png'), input, sized(401, 50))).toBe('The image is larger than 400x400 pixels.');
    expect(await refusalFor(picked('hall.png'), input, sized(50, 401))).toBe('The image is larger than 400x400 pixels.');
  });

  it('accepts an image within every limit', async () => {
    const input = form();

    expect(await refusalFor(picked('hall.png'), input, sized(50, 50))).toBeNull();
  });

  it('accepts any image when the field sets no pixel bounds', async () => {
    const input = form({ attributes: { 'data-min-resolution': '', 'data-max-resolution': null } });

    expect(await refusalFor(picked('hall.png'), input, sized(5000, 1))).toBeNull();
  });

  it('accepts any file of any size in a file field without limits', async () => {
    const input = form({
      attributes: {
        'data-file-extensions': '', 'data-max-filesize': null, 'data-image': null, 'data-dangerous-extensions': null,
      },
    });

    expect(await refusalFor(picked('notes.md', 5000), input)).toBeNull();
  });
});

describe('readImageSize', () => {
  let loads;

  beforeEach(() => {
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:hall', revokeObjectURL: vi.fn() });
    vi.stubGlobal('Image', class {
      set src(url) {
        this.naturalWidth = 64;
        this.naturalHeight = 48;
        queueMicrotask(() => (loads ? this.onload() : this.onerror()));
      }
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reads the size of an image the browser can show', async () => {
    loads = true;

    expect(await readImageSize(picked('hall.png'))).toEqual({ width: 64, height: 48 });
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:hall');
  });

  it('reads nothing from a file the browser cannot show', async () => {
    loads = false;

    expect(await readImageSize(picked('hall.png'))).toBeNull();
  });
});

describe('updateCapacity', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('turns the upload off once a field holds as many files as it can', () => {
    const input = form({ items: item(0), attributes: { 'data-cardinality': '1' } });

    updateCapacity(input);

    expect(input.disabled).toBe(true);
  });

  it('leaves the upload on for a field that holds any number of files', () => {
    const input = form({ items: item(0) + item(1) });

    updateCapacity(input);

    expect(input.disabled).toBe(false);
  });
});

describe('initFileUpload', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.unstubAllGlobals();
  });

  it('finds nothing to do on a page without file fields', () => {
    expect(initFileUpload()).toBe(0);
  });

  it('shows a preview above each attached image, once', () => {
    form({ items: item(0) + item(1, { preview: false }) });

    initFileUpload();
    initFileUpload();

    expect(Array.from(document.querySelectorAll('img')).map((image) => image.getAttribute('src')))
      .toEqual(['/files/hall-0.png']);
  });

  it('removes a file when its remove box is ticked and renumbers the rest', () => {
    const input = form({ items: item(0) + item(1) + item(2, { remove: false }), attributes: { 'data-cardinality': '3' } });
    initFileUpload();
    expect(input.disabled).toBe(true);
    const remove = document.querySelector('[data-name="file-0"] [data-file-remove]');

    remove.dispatchEvent(new Event('change'));
    expect(order()).toEqual(['file-0', 'file-1', 'file-2']);
    remove.checked = true;
    remove.dispatchEvent(new Event('change'));

    expect(order()).toEqual(['file-1', 'file-2']);
    expect(weights()).toEqual(['0', '1']);
    expect(input.disabled).toBe(false);
  });

  it('moves a dragged file to where it is dropped and renumbers the order', () => {
    form({ items: item(0) + item(1) + item(2) });
    initFileUpload();
    const drag = (from, to) => {
      document.querySelector(`[data-name="${from}"]`).dispatchEvent(new Event('dragstart', { bubbles: true }));
      const over = new Event('dragover', { bubbles: true, cancelable: true });
      document.querySelector(`[data-name="${to}"]`).dispatchEvent(over);
      expect(over.defaultPrevented).toBe(true);
      document.querySelector(`[data-name="${to}"]`).dispatchEvent(new Event('drop', { bubbles: true }));
    };

    drag('file-0', 'file-2');
    expect(order()).toEqual(['file-1', 'file-2', 'file-0']);
    drag('file-0', 'file-1');
    expect(order()).toEqual(['file-0', 'file-1', 'file-2']);
    expect(weights()).toEqual(['0', '1', '2']);
  });

  it('leaves the order alone for a drop on the same file, outside the files, or without a drag', () => {
    form({ items: item(0) + item(1) });
    initFileUpload();
    const items = document.querySelector('[data-file-items]');
    const first = document.querySelector('[data-name="file-0"]');

    first.dispatchEvent(new Event('dragstart', { bubbles: true }));
    first.dispatchEvent(new Event('drop', { bubbles: true }));
    items.dispatchEvent(new Event('drop'));
    document.querySelector('[data-name="file-1"]').dispatchEvent(new Event('drop', { bubbles: true }));

    expect(order()).toEqual(['file-0', 'file-1']);
  });

  it('uploads a picked file with the browser request when no other is given', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest);
    FakeRequest.answers = [{ status: 200, body: item(0, { preview: false }) }];
    const input = form({ attributes: { 'data-image': null } });
    initFileUpload();
    choose(input, [picked('plan.png')]);

    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(order()).toEqual(['file-0']));
  });

  it('checks a picked image with the browser image reader when no other is given', async () => {
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:hall', revokeObjectURL: () => {} });
    vi.stubGlobal('Image', class {
      set src(url) {
        queueMicrotask(() => this.onerror());
      }
    });
    const input = form();
    initFileUpload();
    choose(input, [picked('hall.png')]);

    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => expect(document.querySelector('[data-file-upload-error]').textContent)
      .toBe('The file is not an image.'));
  });
});

describe('send', () => {
  beforeEach(() => {
    FakeRequest.answers = [];
    FakeRequest.sent = [];
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.unstubAllGlobals();
  });

  it('posts the file with the field it belongs to, the next position, and the form token', async () => {
    FakeRequest.answers = [{ status: 200, body: item(2) }];
    const input = form({ items: item(0) + item(1) });

    expect(await send(picked('hall.png'), input, () => new FakeRequest())).toBeNull();

    const request = FakeRequest.sent[0];
    expect([request.method, request.url]).toEqual(['POST', '/file/upload']);
    expect(['entity_type', 'bundle', 'field', 'delta', '_csrf'].map((name) => request.body.get(name)))
      .toEqual(['node', 'article', 'photo', '2', 'token-1']);
    expect(request.body.get('file').name).toBe('hall.png');
  });

  it('adds the file the server answers with, with its preview, and turns the upload off when full', async () => {
    FakeRequest.answers = [{ status: 200, body: item(1) }];
    const input = form({ items: item(0), attributes: { 'data-cardinality': '2' } });

    await send(picked('hall.png'), input, () => new FakeRequest());

    expect(order()).toEqual(['file-0', 'file-1']);
    expect(document.querySelectorAll('img')).toHaveLength(1);
    expect(input.disabled).toBe(true);
    expect(document.querySelector('progress')).toBeNull();
  });

  it('shows how far the upload has got while it is sent', async () => {
    FakeRequest.progressShown = [];
    FakeRequest.answers = [{
      status: 200,
      body: '',
      progress: [
        new ProgressEvent('progress', { lengthComputable: true, loaded: 25, total: 100 }),
        new ProgressEvent('progress', { lengthComputable: false }),
      ],
    }];
    const input = form();

    await send(picked('hall.png'), input, () => new FakeRequest());

    expect(FakeRequest.progressShown).toEqual([25, 25]);
  });

  it('answers with the reason the server refused the file', async () => {
    FakeRequest.answers = [{ status: 422, body: 'The file is not an image.' }, { status: 500, body: '' }];
    const input = form();

    expect(await send(picked('hall.png'), input, () => new FakeRequest())).toBe('The file is not an image.');
    expect(await send(picked('hall.png'), input, () => new FakeRequest())).toBe('The file could not be uploaded.');
  });

  it('answers that the upload failed when the request does not get through', async () => {
    FakeRequest.answers = [{ fails: true }];
    const input = form();

    expect(await send(picked('hall.png'), input, () => new FakeRequest())).toBe('The file could not be uploaded.');
    expect(document.querySelector('progress')).toBeNull();
  });

  it('sends no token from a form without one or from outside a form', async () => {
    const withoutToken = form({ csrf: false });
    await send(picked('hall.png'), withoutToken, () => new FakeRequest());
    const outside = form({ inForm: false });
    await send(picked('hall.png'), outside, () => new FakeRequest());

    expect(FakeRequest.sent.map((request) => request.body.has('_csrf'))).toEqual([false, false]);
  });

  it('uses the browser request when no other is given', async () => {
    vi.stubGlobal('XMLHttpRequest', FakeRequest);
    const input = form();

    await send(picked('hall.png'), input);

    expect(FakeRequest.sent).toHaveLength(1);
  });
});

describe('uploadPicked', () => {
  beforeEach(() => {
    FakeRequest.answers = [];
    FakeRequest.sent = [];
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('shows the refusal beside the input without sending the file', async () => {
    const input = form();
    choose(input, [picked('hall.gif'), picked('hall.png', 2000)]);

    await uploadPicked(input, sized(50, 50), () => new FakeRequest());

    expect(FakeRequest.sent).toHaveLength(0);
    expect(input.classList.contains('is-invalid')).toBe(true);
    expect(document.querySelectorAll('[data-file-upload-error]')).toHaveLength(1);
    expect(document.querySelector('[data-file-upload-error]').textContent).toBe('The file is larger than 1000 bytes.');
  });

  it('clears an earlier refusal when the next pick is accepted', async () => {
    FakeRequest.answers = [{ status: 200, body: item(0) }];
    const input = form({ wrapped: false });
    choose(input, [picked('hall.gif')]);
    await uploadPicked(input, sized(50, 50), () => new FakeRequest());
    choose(input, [picked('hall.png')]);

    await uploadPicked(input, sized(50, 50), () => new FakeRequest());

    expect(input.classList.contains('is-invalid')).toBe(false);
    expect(document.querySelector('[data-file-upload-error]')).toBeNull();
    expect(order()).toEqual(['file-0']);
  });

  it('shows the reason the server refused a file', async () => {
    FakeRequest.answers = [{ status: 422, body: 'The image is larger than 400x400 pixels.' }];
    const input = form();
    choose(input, [picked('hall.png')]);

    await uploadPicked(input, sized(50, 50), () => new FakeRequest());

    expect(document.querySelector('[data-file-upload-error]').textContent)
      .toBe('The image is larger than 400x400 pixels.');
  });

  it('stops sending once the field is full', async () => {
    FakeRequest.answers = [{ status: 200, body: item(0) }];
    const input = form({ attributes: { 'data-cardinality': '1' } });
    choose(input, [picked('hall.png'), picked('stage.png')]);

    await uploadPicked(input, sized(50, 50), () => new FakeRequest());

    expect(FakeRequest.sent).toHaveLength(1);
  });

  it('does nothing when no file was picked', async () => {
    const input = form();
    choose(input, null);

    await uploadPicked(input, sized(50, 50), () => new FakeRequest());

    expect(FakeRequest.sent).toHaveLength(0);
  });
});
