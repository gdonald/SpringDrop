// A rich-text editor over each formatted-text textarea. The toolbar offers only
// the buttons whose tags the chosen text format keeps, and follows the format
// choice: a format that keeps no tags, such as plain text, is written in the
// textarea itself. The textarea stays the form's control, so with JavaScript
// off it is what the person writes in, and with it on it is kept in step with
// the editor. The server cleans the text again on every render.

const ALWAYS_KEPT = ['p', 'br'];

const NEVER_KEPT = ['script', 'style', 'iframe', 'object', 'embed', 'template', 'svg', 'math'];

const SAFE_ADDRESS = /^(https?:|mailto:|\/(?!\/)|#)/i;

export const BUTTONS = [
  { id: 'bold', label: 'Bold', tags: ['strong'], run: (editor) => editor.wrap('strong') },
  { id: 'italic', label: 'Italic', tags: ['em'], run: (editor) => editor.wrap('em') },
  { id: 'underline', label: 'Underline', tags: ['u'], run: (editor) => editor.wrap('u') },
  { id: 'strikethrough', label: 'Strikethrough', tags: ['s'], run: (editor) => editor.wrap('s') },
  { id: 'code', label: 'Code', tags: ['code'], run: (editor) => editor.wrap('code') },
  { id: 'subscript', label: 'Subscript', tags: ['sub'], run: (editor) => editor.wrap('sub') },
  { id: 'superscript', label: 'Superscript', tags: ['sup'], run: (editor) => editor.wrap('sup') },
  { id: 'link', label: 'Link', tags: ['a'], run: (editor) => editor.link() },
  { id: 'heading2', label: 'Heading 2', tags: ['h2'], run: (editor) => editor.block('h2') },
  { id: 'heading3', label: 'Heading 3', tags: ['h3'], run: (editor) => editor.block('h3') },
  { id: 'heading4', label: 'Heading 4', tags: ['h4'], run: (editor) => editor.block('h4') },
  { id: 'heading5', label: 'Heading 5', tags: ['h5'], run: (editor) => editor.block('h5') },
  { id: 'heading6', label: 'Heading 6', tags: ['h6'], run: (editor) => editor.block('h6') },
  { id: 'quote', label: 'Quote', tags: ['blockquote'], run: (editor) => editor.block('blockquote') },
  { id: 'bulleted', label: 'Bulleted list', tags: ['ul', 'li'], run: (editor) => editor.list('ul') },
  { id: 'numbered', label: 'Numbered list', tags: ['ol', 'li'], run: (editor) => editor.list('ol') },
  { id: 'image', label: 'Image', tags: ['img'], run: (editor) => editor.pickImage() },
];

// The buttons a format keeping these tags offers.
export function buttonsFor(tags) {
  return BUTTONS.filter((button) => button.tags.every((tag) => tags.includes(tag)));
}

// Keeps the tags a format keeps, with their content, and drops what could run:
// elements that hold scripts, event-handler attributes, and addresses that are
// not web, mail, or same-site ones.
export function clean(html, tags) {
  const template = document.createElement('template');
  template.innerHTML = html;
  const kept = new Set([...tags, ...ALWAYS_KEPT]);
  const walk = (parent) => {
    Array.from(parent.childNodes).forEach((node) => {
      if (node.nodeType !== Node.ELEMENT_NODE) {
        return;
      }
      const tag = node.tagName.toLowerCase();
      if (NEVER_KEPT.includes(tag)) {
        node.remove();
        return;
      }
      walk(node);
      if (!kept.has(tag)) {
        node.replaceWith(...node.childNodes);
        return;
      }
      Array.from(node.attributes).forEach((attribute) => {
        const name = attribute.name.toLowerCase();
        const address = name === 'href' || name === 'src';
        if (name.startsWith('on') || name === 'style' || (address && !SAFE_ADDRESS.test(attribute.value.trim()))) {
          node.removeAttribute(attribute.name);
        }
      });
    });
  };
  walk(template.content);
  return template.innerHTML;
}

function words(value) {
  return (value || '').split(/\s+/).filter((word) => word !== '');
}

function extensionOf(name) {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
}

export class Editor {
  constructor(textarea, options = {}) {
    this.textarea = textarea;
    this.prompt = options.prompt || ((question) => window.prompt(question));
    this.fetch = options.fetch || ((url, init) => window.fetch(url, init));
    this.formats = JSON.parse(textarea.dataset.editorFormats || '{}');
    const field = textarea.closest('fieldset') || textarea.parentElement;
    this.selector = field.querySelector('[data-editor-format-selector]');
    this.wrapper = null;
    this.area = null;
    this.toolbar = null;
    this.message = null;
  }

  tags() {
    const format = this.selector ? this.selector.value : Object.keys(this.formats)[0];
    return this.formats[format] || [];
  }

  // Shows the editor for the chosen format, or the textarea for a format
  // keeping no tags.
  follow() {
    if (this.tags().length === 0) {
      this.detach();
    } else {
      this.attach();
    }
  }

  attach() {
    if (!this.wrapper) {
      this.wrapper = document.createElement('div');
      this.wrapper.className = 'editor mb-2';
      this.toolbar = document.createElement('div');
      this.toolbar.className = 'btn-toolbar gap-1 mb-1';
      this.toolbar.setAttribute('role', 'toolbar');
      this.area = document.createElement('div');
      this.area.className = 'form-control';
      this.area.contentEditable = 'true';
      this.area.setAttribute('data-editor-area', '');
      this.area.setAttribute('role', 'textbox');
      this.area.setAttribute('aria-multiline', 'true');
      this.area.addEventListener('input', () => this.sync());
      this.message = document.createElement('div');
      this.message.className = 'invalid-feedback d-block';
      this.message.hidden = true;
      this.wrapper.append(this.toolbar, this.area, this.message);
      this.textarea.parentNode.insertBefore(this.wrapper, this.textarea.nextSibling);
      this.textarea.hidden = true;
    }
    this.area.innerHTML = clean(this.textarea.value, this.tags());
    this.sync();
    this.toolbar.replaceChildren(...buttonsFor(this.tags()).map((button) => {
      const control = document.createElement('button');
      control.type = 'button';
      control.className = 'btn btn-secondary btn-sm';
      control.textContent = button.label;
      control.setAttribute('data-editor-button', button.id);
      control.addEventListener('click', () => button.run(this));
      return control;
    }));
  }

  detach() {
    if (!this.wrapper) {
      return;
    }
    this.sync();
    this.wrapper.remove();
    this.wrapper = null;
    this.textarea.hidden = false;
  }

  sync() {
    if (this.area) {
      this.textarea.value = this.area.innerHTML;
    }
  }

  range() {
    const selection = document.getSelection();
    if (!selection || selection.rangeCount === 0) {
      return null;
    }
    const range = selection.getRangeAt(0);
    return this.area.contains(range.commonAncestorContainer) ? range : null;
  }

  // Wraps the selected content in an element.
  wrap(tag, attributes = {}) {
    const range = this.range();
    if (!range || range.collapsed) {
      return;
    }
    const element = document.createElement(tag);
    Object.entries(attributes).forEach(([name, value]) => element.setAttribute(name, value));
    element.appendChild(range.extractContents());
    range.insertNode(element);
    this.sync();
  }

  link() {
    const address = (this.prompt('Link address') || '').trim();
    if (SAFE_ADDRESS.test(address)) {
      this.wrap('a', { href: address });
    }
  }

  // The block the selection starts in, made a direct child of the editor
  // area. Text sitting straight in the area is gathered into a paragraph first.
  blockAtSelection() {
    const range = this.range();
    if (!range) {
      return null;
    }
    let node = range.startContainer;
    if (node === this.area) {
      node = this.area.childNodes[range.startOffset] || this.area.lastChild;
    }
    while (node && node.parentNode !== this.area) {
      node = node.parentNode;
    }
    if (!node) {
      return null;
    }
    if (node.nodeType !== Node.ELEMENT_NODE) {
      const paragraph = document.createElement('p');
      node.replaceWith(paragraph);
      paragraph.appendChild(node);
      return paragraph;
    }
    return node;
  }

  // Turns the block into another kind, or back into a paragraph when it
  // already is that kind.
  block(tag) {
    const current = this.blockAtSelection();
    if (!current) {
      return;
    }
    const replacement = document.createElement(current.tagName.toLowerCase() === tag ? 'p' : tag);
    replacement.append(...current.childNodes);
    current.replaceWith(replacement);
    this.sync();
  }

  // Makes the block an item of a new list, a list of the other kind this kind,
  // or a list of this kind back into paragraphs.
  list(tag) {
    const current = this.blockAtSelection();
    if (!current) {
      return;
    }
    const kind = current.tagName.toLowerCase();
    if (kind === 'ul' || kind === 'ol') {
      this.relist(current, tag);
    } else {
      const list = document.createElement(tag);
      const item = document.createElement('li');
      item.append(...current.childNodes);
      list.appendChild(item);
      current.replaceWith(list);
    }
    this.sync();
  }

  relist(current, tag) {
    if (current.tagName.toLowerCase() === tag) {
      const paragraphs = Array.from(current.children).map((item) => {
        const paragraph = document.createElement('p');
        paragraph.append(...item.childNodes);
        return paragraph;
      });
      current.replaceWith(...paragraphs);
    } else {
      const list = document.createElement(tag);
      list.append(...current.childNodes);
      current.replaceWith(list);
    }
  }

  showMessage(text) {
    this.message.textContent = text;
    this.message.hidden = false;
  }

  pickImage() {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = words(this.textarea.dataset.editorImageExtensions).map((extension) => `.${extension}`).join(',');
    input.addEventListener('change', () => this.uploadImage(input.files[0]));
    input.click();
    return input;
  }

  // Checks the image against the limits the server wrote onto the textarea,
  // sends it, and inserts what the server answers with where the selection is.
  async uploadImage(file) {
    if (!file) {
      return;
    }
    this.message.hidden = true;
    const data = this.textarea.dataset;
    const extensions = words(data.editorImageExtensions);
    const maxFilesize = Number(data.editorImageMaxFilesize || 0);
    if (extensions.length > 0 && !extensions.includes(extensionOf(file.name))) {
      this.showMessage(data.editorImageMessageExtension);
      return;
    }
    if (maxFilesize > 0 && file.size > maxFilesize) {
      this.showMessage(data.editorImageMessageSize);
      return;
    }
    const range = this.range();
    const body = new FormData();
    body.append('format', this.selector ? this.selector.value : Object.keys(this.formats)[0]);
    body.append('file', file);
    const token = this.textarea.form && this.textarea.form.querySelector('input[name="_csrf"]');
    if (token) {
      body.append('_csrf', token.value);
    }
    let response;
    try {
      response = await this.fetch(data.editorUpload, { method: 'POST', body });
    } catch {
      this.showMessage('The image could not be uploaded.');
      return;
    }
    if (!response.ok) {
      this.showMessage((await response.text()) || 'The image could not be uploaded.');
      return;
    }
    const image = await response.json();
    const element = document.createElement('img');
    element.setAttribute('src', image.url);
    element.setAttribute('alt', this.prompt('Alternative text') || '');
    element.setAttribute('width', String(image.width));
    element.setAttribute('height', String(image.height));
    element.setAttribute('data-file-id', String(image.id));
    if (range) {
      range.collapse(false);
      range.insertNode(element);
    } else {
      this.area.appendChild(element);
    }
    this.sync();
  }
}

export function initEditors(root = document, options = {}) {
  const textareas = Array.from(root.querySelectorAll('textarea[data-editor-target]'));
  return textareas.map((textarea) => {
    const editor = new Editor(textarea, options);
    editor.follow();
    if (editor.selector) {
      editor.selector.addEventListener('change', () => editor.follow());
    }
    if (textarea.form) {
      textarea.form.addEventListener('submit', () => editor.sync());
    }
    return editor;
  });
}
