const sourceText = document.querySelector('#sourceText');
const sourceLang = document.querySelector('#sourceLang');
const targetLang = document.querySelector('#targetLang');
const translateButton = document.querySelector('#translate');
const status = document.querySelector('#status');
const result = document.querySelector('#result');
const translatedText = document.querySelector('#translatedText');
const copyButton = document.querySelector('#copy');
const selectImageButton = document.querySelector('#selectImage');
const ocrApiKey = document.querySelector('#ocrApiKey');
const ocrLang = document.querySelector('#ocrLang');

document.addEventListener('DOMContentLoaded', loadSettingsAndSelection);
translateButton.addEventListener('click', translateSelection);
copyButton.addEventListener('click', async () => {
  await navigator.clipboard.writeText(translatedText.textContent || '');
  copyButton.textContent = 'Copied';
  window.setTimeout(() => { copyButton.textContent = 'Copy translation'; }, 1200);
});
selectImageButton.addEventListener('click', async () => {
  status.textContent = '';
  await saveSettings();
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) {
    status.textContent = 'No active browser tab was found.';
    return;
  }
  try {
    const response = await chrome.runtime.sendMessage({
      type: 'start-image-selection',
      tabId: tab.id
    });
    if (!response?.ok) throw new Error(response?.error || 'This page cannot be selected.');
    window.close();
  } catch (error) {
    status.textContent = error instanceof Error
      ? error.message
      : 'This page cannot be selected. Try a normal website tab.';
  }
});

async function loadSettingsAndSelection() {
  const settings = await chrome.storage.sync.get({
    sourceLang: 'auto',
    targetLang: 'ar',
    ocrApiKey: '',
    ocrLang: 'eng'
  });
  sourceLang.value = settings.sourceLang;
  targetLang.value = settings.targetLang;
  ocrApiKey.value = settings.ocrApiKey;
  ocrLang.value = settings.ocrLang;

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) return;
  try {
    const response = await chrome.tabs.sendMessage(tab.id, { type: 'get-selection' });
    if (response?.text) sourceText.value = response.text;
  } catch {
    // Restricted browser pages cannot receive content-script messages.
  }
}

async function translateSelection() {
  const text = sourceText.value.trim();
  status.textContent = '';
  result.hidden = true;
  translateButton.disabled = true;
  await saveSettings();

  try {
    const response = await chrome.runtime.sendMessage({
      type: 'translate',
      text,
      sourceLang: sourceLang.value,
      targetLang: targetLang.value
    });
    if (!response?.ok) throw new Error(response?.error || 'Translation failed.');
    translatedText.textContent = response.translatedText;
    result.hidden = false;
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : 'Translation failed.';
  } finally {
    translateButton.disabled = false;
  }
}

function saveSettings() {
  return chrome.storage.sync.set({
    sourceLang: sourceLang.value,
    targetLang: targetLang.value,
    ocrApiKey: ocrApiKey.value.trim(),
    ocrLang: ocrLang.value
  });
}