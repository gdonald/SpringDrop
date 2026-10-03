// Uploads the files picked in a file or image field as soon as they are picked,
// and manages the files already attached: a preview for images, removing, and
// dragging into order. Before a file is sent it is checked against the limits
// the server wrote onto the file input, and a refusal shows the message the
// server wrote beside them, so the browser and the server refuse the same files
// with the same words. The server checks every upload again.

// Mirrors FileService.safeName: the name the server will store the file under.
export function storedName(name, dangerousExtensions) {
  const base = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
  const safe = base.replace(/[^A-Za-z0-9._-]+/g, '_').replace(/^[._]+/, '') || 'file';
  return dangerousExtensions.includes(extensionOf(safe)) ? `${safe}.txt` : safe;
}

export function extensionOf(name) {
  const dot = name.lastIndexOf('.');
  return dot < 0 ? '' : name.substring(dot + 1).toLowerCase();
}

function words(value) {
  return (value || '').split(/\s+/).filter((word) => word !== '');
}

function resolution(value) {
  const match = /^(\d{1,5})x(\d{1,5})$/.exec(value || '');
  return match ? { width: Number(match[1]), height: Number(match[2]) } : null;
}

// Reads an image's size in the browser, or null when it is not an image the
// browser can show.
export function readImageSize(file) {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve({ width: image.naturalWidth, height: image.naturalHeight });
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      resolve(null);
    };
    image.src = url;
  });
}

// The message the server would refuse the file with, or null when it passes.
export async function refusalFor(file, input, imageSize = readImageSize) {
  const data = input.dataset;
  const extensions = words(data.fileExtensions);
  const name = storedName(file.name, words(data.dangerousExtensions));
  if (extensions.length > 0 && !extensions.includes(extensionOf(name))) {
    return data.messageExtension;
  }
  const maxFilesize = Number(data.maxFilesize || 0);
  if (maxFilesize > 0 && file.size > maxFilesize) {
    return data.messageSize;
  }
  if (data.image !== 'true') {
    return null;
  }
  const size = await imageSize(file);
  if (!size) {
    return data.messageNotImage;
  }
  const min = resolution(data.minResolution);
  if (min && (size.width < min.width || size.height < min.height)) {
    return data.messageTooSmall;
  }
  const max = resolution(data.maxResolution);
  if (max && (size.width > max.width || size.height > max.height)) {
    return data.messageTooLarge;
  }
  return null;
}

function itemsOf(input) {
  return input.closest('fieldset').querySelector('[data-file-items]');
}

function attached(items) {
  return Array.from(items.querySelectorAll('[data-file-item]'));
}

function nextDelta(items) {
  return attached(items).reduce((next, item) => Math.max(next, Number(item.dataset.fileItem) + 1), 0);
}

function feedbackFor(input) {
  const field = input.closest('.mb-3') || input.parentElement;
  let feedback = field.querySelector('[data-file-upload-error]');
  if (!feedback) {
    feedback = document.createElement('div');
    feedback.className = 'invalid-feedback d-block';
    feedback.setAttribute('data-file-upload-error', '');
    field.appendChild(feedback);
  }
  return feedback;
}

function showRefusal(input, message) {
  input.classList.add('is-invalid');
  feedbackFor(input).textContent = message;
}

function clearRefusal(input) {
  input.classList.remove('is-invalid');
  const feedback = (input.closest('.mb-3') || input.parentElement).querySelector('[data-file-upload-error]');
  if (feedback) {
    feedback.remove();
  }
}

// Turns the upload input off once the field holds as many files as it can.
export function updateCapacity(input) {
  const cardinality = Number(input.dataset.cardinality);
  input.disabled = cardinality > 0 && attached(itemsOf(input)).length >= cardinality;
}

function addPreview(item) {
  item.querySelectorAll('[data-file-preview]').forEach((name) => {
    if (name.previousElementSibling && name.previousElementSibling.tagName === 'IMG') {
      return;
    }
    const image = document.createElement('img');
    image.src = name.dataset.filePreview;
    image.alt = '';
    image.className = 'img-thumbnail d-block mb-2';
    image.width = 120;
    name.parentNode.insertBefore(image, name);
  });
}

function renumber(items) {
  attached(items).forEach((item, position) => {
    const weight = item.querySelector('[data-file-weight]');
    if (weight) {
      weight.value = position;
    }
  });
}

function enhanceItem(item, input) {
  addPreview(item);
  const remove = item.querySelector('[data-file-remove]');
  if (remove) {
    remove.addEventListener('change', () => {
      if (remove.checked) {
        const items = item.parentNode;
        item.remove();
        renumber(items);
        updateCapacity(input);
      }
    });
  }
}

function enableDragging(items) {
  let dragged = null;
  items.addEventListener('dragstart', (event) => {
    dragged = event.target.closest('[data-file-item]');
  });
  items.addEventListener('dragover', (event) => {
    event.preventDefault();
  });
  items.addEventListener('drop', (event) => {
    event.preventDefault();
    const target = event.target.closest('[data-file-item]');
    if (dragged && target && dragged !== target) {
      const list = attached(items);
      const after = list.indexOf(dragged) < list.indexOf(target);
      items.insertBefore(dragged, after ? target.nextSibling : target);
      renumber(items);
    }
    dragged = null;
  });
}

function csrfToken(form) {
  const token = form && form.querySelector('input[name="_csrf"]');
  return token ? token.value : null;
}

// Sends one file, showing its progress, and adds the inputs the server answers
// with to the attached files. Resolves to the refusal message, or null.
export function send(file, input, createRequest = () => new XMLHttpRequest()) {
  const items = itemsOf(input);
  const body = new FormData();
  body.append('entity_type', input.dataset.entityType);
  body.append('bundle', input.dataset.bundle);
  body.append('field', input.dataset.field);
  body.append('delta', String(nextDelta(items)));
  body.append('file', file);
  const token = csrfToken(input.form);
  if (token) {
    body.append('_csrf', token);
  }

  const progress = document.createElement('progress');
  progress.className = 'w-100';
  progress.max = 100;
  progress.value = 0;
  input.parentNode.insertBefore(progress, input.nextSibling);

  return new Promise((resolve) => {
    const request = createRequest();
    request.upload.addEventListener('progress', (event) => {
      if (event.lengthComputable) {
        progress.value = Math.round((event.loaded / event.total) * 100);
      }
    });
    request.addEventListener('load', () => {
      progress.remove();
      if (request.status !== 200) {
        resolve(request.responseText || 'The file could not be uploaded.');
        return;
      }
      const holder = document.createElement('div');
      holder.innerHTML = request.responseText;
      Array.from(holder.children).forEach((item) => {
        items.appendChild(item);
        enhanceItem(item, input);
      });
      renumber(items);
      updateCapacity(input);
      resolve(null);
    });
    request.addEventListener('error', () => {
      progress.remove();
      resolve('The file could not be uploaded.');
    });
    request.open('POST', input.dataset.uploadUrl);
    request.send(body);
  });
}

export async function uploadPicked(input, imageSize, createRequest) {
  clearRefusal(input);
  const picked = Array.from(input.files || []);
  input.value = '';
  for (const file of picked) {
    if (input.disabled) {
      break;
    }
    const refusal = (await refusalFor(file, input, imageSize)) || (await send(file, input, createRequest));
    if (refusal) {
      showRefusal(input, refusal);
    }
  }
}

export function initFileUpload(root = document, options = {}) {
  const imageSize = options.imageSize || readImageSize;
  const createRequest = options.createRequest || (() => new XMLHttpRequest());
  const inputs = root.querySelectorAll('[data-file-upload]');
  inputs.forEach((input) => {
    const items = itemsOf(input);
    attached(items).forEach((item) => enhanceItem(item, input));
    enableDragging(items);
    updateCapacity(input);
    input.addEventListener('change', () => uploadPicked(input, imageSize, createRequest));
  });
  return inputs.length;
}
