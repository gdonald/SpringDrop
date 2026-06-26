import { afterEach, describe, expect, it, vi } from 'vitest';
import { initConfirm } from '../../main/resources/static/js/confirm-action.js';

describe('initConfirm', () => {
  afterEach(() => {
    document.body.innerHTML = '';
    vi.restoreAllMocks();
  });

  function addLink(message) {
    // href="#" keeps it a real anchor while staying within jsdom's supported
    // hash-only navigation, so the confirm path does not log navigation noise.
    document.body.innerHTML = `<a href="#" data-confirm="${message}">Delete</a>`;
    return document.querySelector('a');
  }

  it('binds only elements carrying a data-confirm message', () => {
    addLink('Are you sure?');
    const bound = initConfirm();
    expect(bound).toBe(1);
  });

  it('allows the action when the user confirms', () => {
    const link = addLink('Are you sure?');
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    initConfirm();

    const event = new window.MouseEvent('click', { cancelable: true });
    link.dispatchEvent(event);

    expect(window.confirm).toHaveBeenCalledWith('Are you sure?');
    expect(event.defaultPrevented).toBe(false);
  });

  it('cancels the action when the user declines', () => {
    const link = addLink('Are you sure?');
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    initConfirm();

    const event = new window.MouseEvent('click', { cancelable: true });
    link.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
  });
});
