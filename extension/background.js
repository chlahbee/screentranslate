const TRANSLATE_URL = 'https://clients5.google.com/translate_a/t';

chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'translate-selection',
    title: 'Translate selected text with ScreenTranslate',
    contexts: ['selection']
  });
  chrome.contextMenus.create({
    id: 'translate-image',
    title: 'Translate text in this image',
    contexts: ['image']
  });
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (!tab?.id) {
    return;
  }

  if (info.menuItemId === 'translate-image' && info.srcUrl) {
    await chrome.tabs.sendMessage(tab.id, { type: 'start-image-selection', imageUrl: info.srcUrl });
    return;
  }
  if (info.menuItemId !== 'translate-selection' || !info.selectionText) {
    return;
  }

  try {
    const settings = await chrome.storage.sync.get({
      sourceLang: 'auto',
      targetLang: 'ar'
    });
    const translatedText = await translate(
      info.selectionText,
      settings.sourceLang,
      settings.targetLang
    );
    await chrome.tabs.sendMessage(tab.id, {
      type: 'show-translation',
      sourceText: info.selectionText,
      translatedText,
      targetLang: settings.targetLang
    });
  } catch (error) {
    await sendError(tab.id, error instanceof Error ? error.message : 'Translation failed');
  }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'start-image-selection') {
    startImageSelection(message.tabId)
      .then(() => sendResponse({ ok: true }))
      .catch((error) => sendResponse({
        ok: false,
        error: error instanceof Error ? error.message : 'This page cannot be selected.'
      }));
    return true;
  }
  if (message?.type === 'capture-region') {
    captureAndTranslate(message.rect, _sender.tab?.id)
      .then(() => sendResponse({ ok: true }))
      .catch(async (error) => {
        if (_sender.tab?.id) await sendError(_sender.tab.id, error instanceof Error ? error.message : 'Translation failed');
        sendResponse({ ok: false });
      });
    return true;
  }
  if (message?.type !== 'translate') {
    if (message?.type !== 'ocr-image') return;
    ocrImage(message.image)
      .then((text) => sendResponse({ ok: true, text }))
      .catch((error) => sendResponse({
        ok: false,
        error: error instanceof Error ? error.message : 'OCR failed'
      }));
    return true;
  }

  translate(message.text, message.sourceLang || 'auto', message.targetLang || 'ar')
    .then((translatedText) => sendResponse({ ok: true, translatedText }))
    .catch((error) => sendResponse({
      ok: false,
      error: error instanceof Error ? error.message : 'Translation failed'
    }));

  return true;
});

async function startImageSelection(tabId) {
  if (!tabId) throw new Error('No active browser tab was found.');
  try {
    await chrome.tabs.sendMessage(tabId, { type: 'start-image-selection' });
  } catch {
    await chrome.scripting.executeScript({
      target: { tabId },
      files: ['content.js']
    });
    await chrome.scripting.insertCSS({
      target: { tabId },
      files: ['content.css']
    });
    await chrome.tabs.sendMessage(tabId, { type: 'start-image-selection' });
  }
}

async function captureAndTranslate(rect, tabId) {
  if (!tabId) throw new Error('No active page was found.');
  const dataUrl = await chrome.tabs.captureVisibleTab(null, { format: 'png' });
  const croppedImage = await cropImage(dataUrl, rect);
  const text = await ocrImage(croppedImage);
  const settings = await chrome.storage.sync.get({ sourceLang: 'auto', targetLang: 'ar' });
  const translatedText = await translate(text, settings.sourceLang, settings.targetLang);
  await chrome.tabs.sendMessage(tabId, {
    type: 'show-image-translation',
    rect,
    sourceText: text,
    translatedText,
    targetLang: settings.targetLang
  });
}

async function cropImage(dataUrl, rect) {
  const response = await fetch(dataUrl);
  const blob = await response.blob();
  const bitmap = await createImageBitmap(blob);
  const scaleX = bitmap.width / rect.viewportWidth;
  const scaleY = bitmap.height / rect.viewportHeight;
  const canvas = new OffscreenCanvas(
    Math.max(1, Math.round(rect.width * scaleX)),
    Math.max(1, Math.round(rect.height * scaleY))
  );
  const context = canvas.getContext('2d');
  context.drawImage(
    bitmap,
    Math.round(rect.left * scaleX),
    Math.round(rect.top * scaleY),
    Math.round(rect.width * scaleX),
    Math.round(rect.height * scaleY),
    0,
    0,
    canvas.width,
    canvas.height
  );
  const cropped = await canvas.convertToBlob({ type: 'image/png' });
  return `data:image/png;base64,${arrayBufferToBase64(await cropped.arrayBuffer())}`;
}

function arrayBufferToBase64(buffer) {
  let binary = '';
  for (const byte of new Uint8Array(buffer)) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function ocrImage(image) {
  const settings = await chrome.storage.sync.get({ ocrApiKey: '', ocrLang: 'eng' });
  if (!settings.ocrApiKey) {
    throw new Error('OCR API key missing. Open ScreenTranslate and add a free OCR.space API key.');
  }
  const body = new FormData();
  body.append('base64Image', image);
  body.append('language', settings.ocrLang);
  body.append('isOverlayRequired', 'false');
  body.append('OCREngine', '2');
  const response = await fetch('https://api.ocr.space/parse/image', {
    method: 'POST',
    headers: { apikey: settings.ocrApiKey },
    body
  });
  if (!response.ok) throw new Error(`OCR service returned ${response.status}.`);
  const data = await response.json();
  if (data.IsErroredOnProcessing || !Array.isArray(data.ParsedResults)) {
    const serviceError = Array.isArray(data.ErrorMessage)
      ? data.ErrorMessage.join(' ')
      : '';
    throw new Error(serviceError || 'Could not read text from this image.');
  }
  const text = data.ParsedResults.map((item) => item.ParsedText || '').join('\n').trim();
  if (!text) throw new Error('No readable text was found in the selected area.');
  return text;
}

async function translate(text, sourceLang, targetLang) {
  const trimmedText = String(text || '').trim();
  if (!trimmedText) {
    throw new Error('Select or enter some text first.');
  }
  if (trimmedText.length > 5000) {
    throw new Error('Text is too long. Please translate up to 5,000 characters at a time.');
  }

  const params = new URLSearchParams({
    client: 'dict-chrome-ex',
    sl: sourceLang || 'auto',
    tl: targetLang || 'ar',
    q: trimmedText
  });
  const response = await fetch(`${TRANSLATE_URL}?${params.toString()}`, {
    headers: { Accept: 'application/json' }
  });
  if (!response.ok) {
    throw new Error(`Translation service returned ${response.status}.`);
  }

  const data = await response.json();
  if (!Array.isArray(data) || typeof data[0] !== 'string' || !data[0].trim()) {
    throw new Error('The translation service returned an invalid response.');
  }
  return data[0];
}

async function sendError(tabId, error) {
  try {
    await chrome.tabs.sendMessage(tabId, { type: 'translation-error', error });
  } catch {
    // The page may not allow content scripts (for example, a browser settings page).
  }
}