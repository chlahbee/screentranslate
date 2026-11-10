(() => {
  let actionButton;
  let resultPanel;
  let selectionLayer;
  let selectionStart;
  let selecting = false;

  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type === 'get-selection') {
      sendResponse({ text: window.getSelection()?.toString().trim() || '' });
      return true;
    }
    if (message?.type === 'start-image-selection') {
      startImageSelection();
      return;
    }
    if (message?.type === 'show-translation') {
      showResult(message.sourceText, message.translatedText, message.targetLang);
    } else if (message?.type === 'show-image-translation') {
      showImageTranslation(message);
    } else if (message?.type === 'translation-error') {
      showError(message.error);
    }
  });

  function startImageSelection() {
    if (selectionLayer) selectionLayer.remove();
    selectionLayer = document.createElement('div');
    selectionLayer.className = 'screentranslate-selection-layer';
    selectionLayer.innerHTML = '<div class="screentranslate-selection-help">Drag over text in an image · Esc to cancel</div><div class="screentranslate-selection-box"></div>';
    document.documentElement.appendChild(selectionLayer);
    const box = selectionLayer.querySelector('.screentranslate-selection-box');
    selectionLayer.addEventListener('mousedown', (event) => {
      if (event.button !== 0) return;
      selecting = true;
      selectionStart = { x: event.clientX, y: event.clientY };
      box.style.display = 'block';
      updateSelection(box, selectionStart.x, selectionStart.y, selectionStart.x, selectionStart.y);
    });
    selectionLayer.addEventListener('mousemove', (event) => {
      if (!selecting) return;
      updateSelection(box, selectionStart.x, selectionStart.y, event.clientX, event.clientY);
    });
    selectionLayer.addEventListener('mouseup', (event) => {
      if (!selecting) return;
      selecting = false;
      const rect = normalizedRect(selectionStart.x, selectionStart.y, event.clientX, event.clientY);
      if (rect.width < 8 || rect.height < 8) return;
      selectionLayer.remove();
      selectionLayer = null;
      chrome.runtime.sendMessage({
        type: 'capture-region',
        rect: {
          ...rect,
          viewportWidth: window.innerWidth,
          viewportHeight: window.innerHeight
        }
      }).catch(() => showError('Could not capture this page.'));
    });
    document.addEventListener('keydown', cancelSelection, { once: true });
  }

  function cancelSelection(event) {
    if (event.key === 'Escape' && selectionLayer) {
      selectionLayer.remove();
      selectionLayer = null;
    }
  }

  function normalizedRect(x1, y1, x2, y2) {
    return {
      left: Math.min(x1, x2),
      top: Math.min(y1, y2),
      width: Math.abs(x2 - x1),
      height: Math.abs(y2 - y1)
    };
  }

  function updateSelection(box, x1, y1, x2, y2) {
    const rect = normalizedRect(x1, y1, x2, y2);
    box.style.left = `${rect.left}px`;
    box.style.top = `${rect.top}px`;
    box.style.width = `${rect.width}px`;
    box.style.height = `${rect.height}px`;
  }

  function showActionButton(text, rect) {
    if (!actionButton) {
      actionButton = document.createElement('button');
      actionButton.className = 'screentranslate-action';
      actionButton.type = 'button';
      actionButton.textContent = 'ترجم';
      document.documentElement.appendChild(actionButton);
    }

    actionButton.style.left = `${Math.max(8, Math.min(window.innerWidth - 68, rect.left + rect.width / 2 - 30))}px`;
    actionButton.style.top = `${Math.max(8, rect.top + window.scrollY - 42)}px`;
    actionButton.onclick = () => {
      hideActionButton();
      chrome.runtime.sendMessage({
        type: 'translate',
        text,
        sourceLang: 'auto',
        targetLang: 'ar'
      }).then((response) => {
        if (response?.ok) {
          showResult(text, response.translatedText, 'ar');
        } else {
          showError(response?.error || 'Translation failed.');
        }
      }).catch(() => showError('Could not connect to ScreenTranslate.'));
    };
    actionButton.hidden = false;
  }

  function showResult(sourceText, translatedText, targetLang) {
    if (!resultPanel) {
      resultPanel = document.createElement('aside');
      resultPanel.className = 'screentranslate-panel';
      document.documentElement.appendChild(resultPanel);
    }
    resultPanel.innerHTML = '';

    const header = document.createElement('div');
    header.className = 'screentranslate-header';
    header.textContent = `ScreenTranslate · ${String(targetLang || 'ar').toUpperCase()}`;

    const close = document.createElement('button');
    close.className = 'screentranslate-close';
    close.type = 'button';
    close.textContent = '×';
    close.setAttribute('aria-label', 'Close translation');
    close.onclick = () => resultPanel.remove();
    header.appendChild(close);

    const original = document.createElement('div');
    original.className = 'screentranslate-original';
    original.textContent = sourceText;
    const translation = document.createElement('div');
    translation.className = 'screentranslate-translation';
    translation.textContent = translatedText;
    resultPanel.append(header, original, translation);
    resultPanel.hidden = false;
  }

  function showImageTranslation(message) {
    showResult(message.sourceText, message.translatedText, message.targetLang);
  }

  function showError(message) {
    showResult('Error', message, '');
  }

  function hideActionButton() {
    if (actionButton) actionButton.hidden = true;
  }
})();