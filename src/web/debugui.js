/* Shared browser text/IME and focus bridge for the Dear ImGui overlay. */
'use strict';
function s2InstallDebugUi(module, canvas, onOpen) {
  const input = document.createElement('textarea');
  input.setAttribute('aria-label', 'Developer menu text input');
  input.autocomplete = 'off'; input.spellcheck = false;
  Object.assign(input.style, {position:'fixed', opacity:'0.01', width:'1px', height:'20px',
    padding:'0', border:'0', resize:'none', zIndex:'20', display:'none'});
  document.body.append(input);
  let open = false, composing = false, confirmed = '';
  function commit(text) {
    if (!open || !text) return;
    module.ccall('s2DebugUiCommit', null, ['string'], [text]);
  }
  input.addEventListener('compositionstart', () => { composing = true; confirmed = ''; });
  input.addEventListener('compositionend', event => {
    composing = false; confirmed = event.data || ''; commit(confirmed); input.value = '';
  });
  input.addEventListener('input', event => {
    if (composing || event.isComposing) return;
    if (confirmed && event.data === confirmed && /Composition/i.test(event.inputType || '')) {
      confirmed = ''; input.value = ''; return;
    }
    confirmed = ''; commit(input.value); input.value = '';
  });
  // SDL still receives navigation and modifier keys, but text has one owner.
  // Browser paste commits through textarea input; avoid a second SDL paste.
  input.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && event.code === 'KeyV') event.stopImmediatePropagation();
    if (composing || event.isComposing) event.stopImmediatePropagation();
  });
  input.addEventListener('keypress', event => event.stopImmediatePropagation());
  module.s2DebugUiSetOpen = value => {
    open = Boolean(value); composing = false; input.value = '';
    if (open) document.exitPointerLock();
    else { input.style.display = 'none'; canvas.focus(); }
    onOpen(open);
  };
  module.s2DebugUiIme = (visible, x, y, height) => {
    if (!open || !visible) {
      input.style.display = 'none';
      if (document.activeElement === input) canvas.focus();
      return;
    }
    const rect = canvas.getBoundingClientRect();
    Object.assign(input.style, {display:'block', left:(rect.left + x * rect.width / canvas.width)+'px',
      top:(rect.top + y * rect.height / canvas.height)+'px', height:Math.max(1,height * rect.height / canvas.height)+'px'});
    if (document.activeElement !== input) { input.value = ''; input.focus({preventScroll:true}); }
  };
  return input;
}
