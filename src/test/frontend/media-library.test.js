import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  MediaLibrary, initMediaLibrary, insert, updateCapacity,
} from '../../main/resources/static/js/media-library.js';

function chosenItem(delta, id, { thumbnail = true } = {}) {
  return `<div class="list-group-item" data-media-item="${id}" draggable="true">`
    + `<input type="hidden" name="field_media[${delta}]:target_id" value="${id}">`
    + `<p${thumbnail ? ` data-media-thumbnail="/files/styles/thumbnail/public/${id}.png"` : ''}>Item ${id}</p>`
    + `<input type="number" data-media-weight value="${delta}">`
    + '<input type="checkbox" data-media-remove>'
    + '</div>';
}

function field({ items = '', cardinality = -1, token = true } = {}) {
  document.body.innerHTML = '<form>'
    + (token ? '<input type="hidden" name="_csrf" value="token-1">' : '')
    + `<fieldset data-media-field="field_media" data-cardinality="${cardinality}">`
    + `<div data-media-items>${items}</div>`
    + '<a href="/media-library?entity_type=node&bundle=page&field=field_media" data-media-library-open>Add media</a>'
    + '</fieldset></form>';
  return document.querySelector('[data-media-field]');
}

function libraryItem(id, checked = false) {
  return `<div class="col" data-media-library-item><label><input type="checkbox" data-media-library-choice`
    + ` value="${id}"${checked ? ' checked' : ''}><span>Item ${id}</span></label>`
    + `<template data-media-library-inputs>${chosenItem('__delta__', id)}</template></div>`;
}

function library({ items = [libraryItem(7), libraryItem(8)], upload = true } = {}) {
  return '<div data-media-library>'
    + '<a href="/media-library?type=photo" data-media-library-tab>Photo</a>'
    + '<a href="/media-library?type=talk" data-media-library-tab>Talk</a>'
    + '<form action="/media-library" data-media-library-search><input name="type" value="photo">'
    + '<input name="q" value="hall"></form>'
    + (upload ? '<form action="/media-library/add" data-media-library-upload><input name="type" value="photo">'
      + '<input name="name" value="Hall"><div data-media-library-message hidden></div></form>' : '')
    + (items.length === 0 ? '<p data-media-library-empty>There is no media here yet.</p>' : '')
    + `<div data-media-library-results>${items.join('')}</div>`
    + '<button type="button" data-media-library-insert>Insert selected</button></div>';
}

function page(html, ok = true) {
  return Promise.resolve({ ok, text: () => Promise.resolve(html) });
}

function ids(fieldElement) {
  return Array.from(fieldElement.querySelectorAll('[data-media-items] input[type="hidden"]'))
    .map((input) => `${input.name}=${input.value}`);
}

function flush() {
  return new Promise((resolve) => { setTimeout(resolve, 0); });
}

afterEach(() => {
  document.body.innerHTML = '';
  vi.unstubAllGlobals();
});

describe('initMediaLibrary', () => {
  it('previews the chosen media once and offers the library while there is room', () => {
    const fieldElement = field({ items: chosenItem(0, 3) + chosenItem(1, 4, { thumbnail: false }), cardinality: 3 });

    initMediaLibrary();
    initMediaLibrary();

    expect(Array.from(fieldElement.querySelectorAll('img')).map((image) => image.getAttribute('src')))
      .toEqual(['/files/styles/thumbnail/public/3.png']);
    expect(fieldElement.querySelector('[data-media-library-open]').getAttribute('aria-disabled')).toBe('false');
  });

  it('removes a chosen item when its remove box is ticked, renumbering and making room', () => {
    const fieldElement = field({ items: chosenItem(0, 3) + chosenItem(1, 4), cardinality: 2 });
    initMediaLibrary();
    expect(fieldElement.querySelector('[data-media-library-open]').classList.contains('disabled')).toBe(true);
    const remove = fieldElement.querySelector('[data-media-remove]');

    remove.dispatchEvent(new Event('change'));
    expect(ids(fieldElement)).toHaveLength(2);
    remove.checked = true;
    remove.dispatchEvent(new Event('change'));

    expect(ids(fieldElement)).toEqual(['field_media[1]:target_id=4']);
    expect(fieldElement.querySelector('[data-media-weight]').value).toBe('0');
    expect(fieldElement.querySelector('[data-media-library-open]').classList.contains('disabled')).toBe(false);
  });

  it('moves a dragged item to where it is dropped', () => {
    const fieldElement = field({ items: chosenItem(0, 3) + chosenItem(1, 4) + chosenItem(2, 5) });
    initMediaLibrary();
    const items = () => Array.from(fieldElement.querySelectorAll('[data-media-item]')).map((item) => item.dataset.mediaItem);
    const drag = (from, to) => {
      fieldElement.querySelector(`[data-media-item="${from}"]`).dispatchEvent(new Event('dragstart', { bubbles: true }));
      const over = new Event('dragover', { bubbles: true, cancelable: true });
      fieldElement.querySelector(`[data-media-item="${to}"]`).dispatchEvent(over);
      expect(over.defaultPrevented).toBe(true);
      fieldElement.querySelector(`[data-media-item="${to}"]`).dispatchEvent(new Event('drop', { bubbles: true }));
    };

    drag('3', '5');
    expect(items()).toEqual(['4', '5', '3']);
    drag('3', '4');
    expect(items()).toEqual(['3', '4', '5']);
    drag('4', '4');
    fieldElement.querySelector('[data-media-items]').dispatchEvent(new Event('drop'));
    expect(items()).toEqual(['3', '4', '5']);
  });

  it('opens the library in a dialog and loads it, but not once the field is full', async () => {
    const fetch = vi.fn(() => page(library()));
    field({ items: chosenItem(0, 3), cardinality: 1 });
    initMediaLibrary(document, { fetch });

    document.querySelector('[data-media-library-open]').click();
    expect(fetch).not.toHaveBeenCalled();

    field();
    initMediaLibrary(document, { fetch });
    document.querySelector('[data-media-library-open]').click();
    await flush();

    expect(fetch).toHaveBeenCalledWith('/media-library?entity_type=node&bundle=page&field=field_media',
      { headers: { Accept: 'text/html' } });
    expect(document.querySelectorAll('dialog [data-media-library-choice]')).toHaveLength(2);
  });
});

describe('MediaLibrary', () => {
  async function opened(fetch, options = {}) {
    const fieldElement = field(options);
    const libraryDialog = new MediaLibrary(fieldElement, { fetch });
    await libraryDialog.open('/media-library?field=field_media');
    return { fieldElement, libraryDialog };
  }

  it('follows the tabs and the search inside the dialog', async () => {
    const fetch = vi.fn(() => page(library()));
    const { libraryDialog } = await opened(fetch);

    libraryDialog.dialog.querySelectorAll('[data-media-library-tab]')[1].click();
    await flush();
    libraryDialog.dialog.querySelector('[data-media-library-search]')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await flush();

    expect(fetch.mock.calls.map((call) => call[0])).toEqual(['/media-library?field=field_media',
      '/media-library?type=talk', '/media-library?type=photo&q=hall']);
  });

  it('inserts the chosen media at the next positions, leaving out what the field holds', async () => {
    const { fieldElement, libraryDialog } = await opened(() => page(library({
      items: [libraryItem(3, true), libraryItem(7, true), libraryItem(8, true), libraryItem(9)],
    })), { items: chosenItem(4, 3) });

    libraryDialog.dialog.querySelector('[data-media-library-insert]').click();

    expect(ids(fieldElement)).toEqual(['field_media[4]:target_id=3', 'field_media[5]:target_id=7',
      'field_media[6]:target_id=8']);
    expect(Array.from(fieldElement.querySelectorAll('[data-media-weight]')).map((weight) => weight.value))
      .toEqual(['0', '1', '2']);
    expect(libraryDialog.dialog.hasAttribute('open')).toBe(false);
    expect(fieldElement.querySelectorAll('img')).toHaveLength(2);
  });

  it('inserts no more media than the field has room for', () => {
    const fieldElement = field({ cardinality: 1 });
    document.body.insertAdjacentHTML('beforeend', library({ items: [libraryItem(7, true), libraryItem(8, true)] }));

    insert(fieldElement, document.querySelector('[data-media-library]'));

    expect(ids(fieldElement)).toEqual(['field_media[0]:target_id=7']);
  });

  it('closes when asked', async () => {
    const { libraryDialog } = await opened(() => page(library()));

    libraryDialog.dialog.querySelector('[data-media-library-close]').click();

    expect(libraryDialog.dialog.hasAttribute('open')).toBe(false);
  });

  it('adds an uploaded item first in the list, chosen, with the form token', async () => {
    const answers = [page(library({ items: [] })), page(libraryItem(12, true))];
    const fetch = vi.fn(() => answers.shift());
    const { libraryDialog } = await opened(fetch);
    const form = libraryDialog.dialog.querySelector('[data-media-library-upload]');

    form.dispatchEvent(new Event('submit', { cancelable: true }));
    await flush();

    const [url, init] = fetch.mock.calls[1];
    expect(url).toBe('/media-library/add');
    expect([init.method, init.body.get('type'), init.body.get('_csrf')]).toEqual(['POST', 'photo', 'token-1']);
    expect(libraryDialog.dialog.querySelector('[data-media-library-results] [data-media-library-choice]').value)
      .toBe('12');
    expect(libraryDialog.dialog.querySelector('[data-media-library-empty]')).toBeNull();
  });

  it('shows why an upload was refused, or that it failed', async () => {
    const answers = [page(library()), page('Choose a file to upload.', false), page('', false),
      Promise.reject(new Error('down'))];
    const { libraryDialog } = await opened(() => answers.shift(), { token: false });
    const form = libraryDialog.dialog.querySelector('[data-media-library-upload]');
    const message = form.querySelector('[data-media-library-message]');

    await libraryDialog.upload(form);
    expect(message.textContent).toBe('Choose a file to upload.');
    await libraryDialog.upload(form);
    expect(message.textContent).toBe('The media could not be added.');
    await libraryDialog.upload(form);
    expect(message.textContent).toBe('The media could not be added.');
    expect(message.hidden).toBe(false);
  });

  it('adds an upload to a list that was not empty', async () => {
    const answers = [page(library()), page(libraryItem(12, true))];
    const { libraryDialog } = await opened(() => answers.shift());

    await libraryDialog.upload(libraryDialog.dialog.querySelector('[data-media-library-upload]'));

    expect(libraryDialog.dialog.querySelectorAll('[data-media-library-choice]')).toHaveLength(3);
  });

  it('has no upload form for someone who may not add media', async () => {
    const { libraryDialog } = await opened(() => page(library({ upload: false })));

    expect(libraryDialog.dialog.querySelector('[data-media-library-upload]')).toBeNull();
  });

  it('opens as a modal dialog where the browser supports one', async () => {
    const libraryDialog = new MediaLibrary(field(), { fetch: () => page(library()) });
    libraryDialog.dialog.showModal = vi.fn(() => libraryDialog.dialog.setAttribute('open', ''));
    libraryDialog.dialog.close = vi.fn();

    await libraryDialog.open('/media-library');
    libraryDialog.close();

    expect(libraryDialog.dialog.showModal).toHaveBeenCalled();
    expect(libraryDialog.dialog.close).toHaveBeenCalled();
  });

  it('opens without dialog support and uses the browser fetch when none is given', async () => {
    vi.stubGlobal('fetch', () => page(library()));
    const fieldElement = field();
    const libraryDialog = new MediaLibrary(fieldElement);
    libraryDialog.dialog.showModal = undefined;
    libraryDialog.dialog.close = undefined;

    await libraryDialog.open('/media-library');
    expect(libraryDialog.dialog.hasAttribute('open')).toBe(true);
    libraryDialog.close();

    expect(libraryDialog.dialog.hasAttribute('open')).toBe(false);
  });
});

describe('updateCapacity', () => {
  it('marks the library unavailable once the field is full', () => {
    const fieldElement = field({ items: chosenItem(0, 3), cardinality: 1 });

    updateCapacity(fieldElement);

    expect(fieldElement.querySelector('[data-media-library-open]').getAttribute('aria-disabled')).toBe('true');
  });
});
