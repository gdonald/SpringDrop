// Opens the media library for a media reference field in a dialog, and puts
// the media chosen there into the field. The library's tabs, search, and
// upload form are answered by the server with the library again, or with the
// item just added. Each listed item carries the field's inputs for it, with a
// placeholder where its position goes, so the browser only fills in the
// position. The chosen media can be dragged into order or removed, and the
// field stops offering the library once it holds as many items as it can.

const PLACEHOLDER = /__delta__/g;

function itemsOf(field) {
  return field.querySelector('[data-media-items]');
}

function chosen(field) {
  return Array.from(itemsOf(field).querySelectorAll('[data-media-item]'));
}

function nextDelta(field) {
  return chosen(field).reduce((next, item) => {
    const input = item.querySelector('input[type="hidden"]');
    const match = /\[(\d+)\]/.exec(input.name);
    return Math.max(next, Number(match[1]) + 1);
  }, 0);
}

function room(field) {
  const cardinality = Number(field.dataset.cardinality);
  return cardinality < 0 ? Infinity : cardinality - chosen(field).length;
}

export function updateCapacity(field) {
  const opener = field.querySelector('[data-media-library-open]');
  const full = room(field) <= 0;
  opener.classList.toggle('disabled', full);
  opener.setAttribute('aria-disabled', String(full));
}

function renumber(field) {
  chosen(field).forEach((item, position) => {
    item.querySelector('[data-media-weight]').value = position;
  });
}

function enhanceItem(item, field) {
  item.querySelectorAll('[data-media-thumbnail]').forEach((name) => {
    if (name.previousElementSibling && name.previousElementSibling.tagName === 'IMG') {
      return;
    }
    const image = document.createElement('img');
    image.src = name.dataset.mediaThumbnail;
    image.alt = '';
    image.className = 'img-thumbnail d-block mb-2';
    image.width = 100;
    name.parentNode.insertBefore(image, name);
  });
  const remove = item.querySelector('[data-media-remove]');
  remove.addEventListener('change', () => {
    if (remove.checked) {
      item.remove();
      renumber(field);
      updateCapacity(field);
    }
  });
}

function enableDragging(field) {
  const items = itemsOf(field);
  let dragged = null;
  items.addEventListener('dragstart', (event) => {
    dragged = event.target.closest('[data-media-item]');
  });
  items.addEventListener('dragover', (event) => event.preventDefault());
  items.addEventListener('drop', (event) => {
    event.preventDefault();
    const target = event.target.closest('[data-media-item]');
    if (dragged && target && dragged !== target) {
      const list = chosen(field);
      const after = list.indexOf(dragged) < list.indexOf(target);
      items.insertBefore(dragged, after ? target.nextSibling : target);
      renumber(field);
    }
    dragged = null;
  });
}

// Puts the media chosen in the library into the field, as many as it has room
// for, leaving out media it already holds.
export function insert(field, library) {
  const held = new Set(chosen(field).map((item) => item.dataset.mediaItem));
  let space = room(field);
  library.querySelectorAll('[data-media-library-choice]:checked').forEach((choice) => {
    if (space <= 0 || held.has(choice.value)) {
      return;
    }
    const template = choice.closest('[data-media-library-item]').querySelector('[data-media-library-inputs]');
    const holder = document.createElement('div');
    holder.innerHTML = template.innerHTML.replace(PLACEHOLDER, String(nextDelta(field)));
    const item = holder.firstElementChild;
    itemsOf(field).appendChild(item);
    enhanceItem(item, field);
    held.add(choice.value);
    space -= 1;
  });
  renumber(field);
  updateCapacity(field);
}

function csrfToken(field) {
  const form = field.closest('form');
  const token = form && form.querySelector('input[name="_csrf"]');
  return token ? token.value : null;
}

export class MediaLibrary {
  constructor(field, options = {}) {
    this.field = field;
    this.fetch = options.fetch || ((url, init) => window.fetch(url, init));
    this.dialog = document.createElement('dialog');
    this.dialog.className = 'media-library-dialog w-75';
    this.dialog.setAttribute('data-media-library-dialog', '');
    document.body.appendChild(this.dialog);
  }

  async open(url) {
    if (typeof this.dialog.showModal === 'function') {
      this.dialog.showModal();
    } else {
      this.dialog.setAttribute('open', '');
    }
    await this.load(url);
  }

  close() {
    if (typeof this.dialog.close === 'function') {
      this.dialog.close();
    }
    this.dialog.removeAttribute('open');
  }

  async load(url) {
    const response = await this.fetch(url, { headers: { Accept: 'text/html' } });
    this.dialog.innerHTML = '<div class="d-flex justify-content-end"><button type="button" class="btn btn-secondary btn-sm"'
      + ' data-media-library-close>Close</button></div>' + (await response.text());
    this.wire();
  }

  wire() {
    this.dialog.querySelector('[data-media-library-close]').addEventListener('click', () => this.close());
    this.dialog.querySelectorAll('[data-media-library-tab]').forEach((tab) => {
      tab.addEventListener('click', (event) => {
        event.preventDefault();
        this.load(tab.getAttribute('href'));
      });
    });
    const search = this.dialog.querySelector('[data-media-library-search]');
    search.addEventListener('submit', (event) => {
      event.preventDefault();
      const query = new URLSearchParams(new FormData(search)).toString();
      this.load(`${search.getAttribute('action')}?${query}`);
    });
    const upload = this.dialog.querySelector('[data-media-library-upload]');
    if (upload) {
      upload.addEventListener('submit', (event) => {
        event.preventDefault();
        this.upload(upload);
      });
    }
    this.dialog.querySelector('[data-media-library-insert]').addEventListener('click', () => {
      insert(this.field, this.dialog);
      this.close();
    });
  }

  // Adds a new item through the library's form and lists it first, chosen.
  async upload(form) {
    const message = form.querySelector('[data-media-library-message]');
    message.hidden = true;
    const body = new FormData(form);
    const token = csrfToken(this.field);
    if (token) {
      body.append('_csrf', token);
    }
    let response;
    try {
      response = await this.fetch(form.getAttribute('action'), { method: 'POST', body });
    } catch {
      response = null;
    }
    if (!response || !response.ok) {
      message.textContent = (response && (await response.text())) || 'The media could not be added.';
      message.hidden = false;
      return;
    }
    const holder = document.createElement('div');
    holder.innerHTML = await response.text();
    const results = this.dialog.querySelector('[data-media-library-results]');
    results.insertBefore(holder.firstElementChild, results.firstChild);
    const empty = this.dialog.querySelector('[data-media-library-empty]');
    if (empty) {
      empty.remove();
    }
    form.reset();
  }
}

export function initMediaLibrary(root = document, options = {}) {
  const fields = Array.from(root.querySelectorAll('[data-media-field]'));
  return fields.map((field) => {
    const library = new MediaLibrary(field, options);
    chosen(field).forEach((item) => enhanceItem(item, field));
    enableDragging(field);
    updateCapacity(field);
    const opener = field.querySelector('[data-media-library-open]');
    opener.addEventListener('click', (event) => {
      event.preventDefault();
      if (room(field) > 0) {
        library.open(opener.getAttribute('href'));
      }
    });
    return library;
  });
}
