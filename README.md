<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/bdb1079a-4da3-431d-aa30-f6a81a3a2f7d

## Run Locally

**Prerequisites:**  Node.js


1. Install dependencies:
   `npm install`
2. Set the `GEMINI_API_KEY` in [.env.local](.env.local) to your Gemini API key
3. Run the app:
   `npm run dev`

## Browser extension

The PC browser version is in [`extension/`](./extension). It supports selecting a region
inside an image or screenshot, reading the text with OCR, and placing the translation over
the selected region. It also has a right-click image menu and configurable languages.

To load it in Chrome or Edge:

1. Open `chrome://extensions` (or `edge://extensions`).
2. Enable **Developer mode**.
3. Choose **Load unpacked** and select this repository's `extension` folder.

Click the extension icon, choose **Select area in an image**, then drag over the text.
Add a free OCR.space API key in the popup first. The selected image region is sent to
OCR.space, so an internet connection is required.
