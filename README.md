# ⚡ CapyWarp

<p align="left">
  <img src=".github/images/capyWarpLogo.png" alt="CapyLinker Logo" width="300"/>
</p>

CapyWarp is a lightweight Android utility that integrates into the OS, letting you send selected text or shared images to your favorite AI model via prompts you define.

It works from anywhere using the Android share sheet (for images) or the text selection menu (`PROCESS_TEXT`) and delivers the result back as a notification, clipboard copy, or saved image—all configurable per prompt.

## ✨ Key Features

### 🤖 AI Integration

* **Text & Image Input**: Send selected text or shared photos/screenshots to your AI from any app.
* **Flexible Output**: Configure prompts to receive responses as either text or generated images.
* **Powerful Prompt Management**: Define templates using the `$TEXT` variable and set input/output types and result handling for each prompt.
* **One-tap `$TEXT` Insert**: Insert the `$TEXT` token into your template via a dedicated button in the prompt editor.

### ⚡ The 'Warp!' Core Experience

* **Floating Panel**: The 'Warp!' floating panel appears on top of your current app for quick prompt selection.
* **OS Integration**: Works directly from the Android text selection menu (`PROCESS_TEXT`) and the system Share Sheet for instant access anywhere.

### 🧩 Prompt List

* **Reorder Mode (Top‑right)**: Tap the Reorder icon in the top‑right to enter reorder mode. Use the Up/Down buttons on each item to move it. Changes are saved immediately and persist.
* **Multi‑select Actions in App Bar**: Long‑press an item to select. When one item is selected, a Duplicate icon appears; when one or more are selected, a Delete icon appears. No bottom text buttons — all actions are in the top app bar.

### ⚙️ User Customization

* **Per-Prompt Result Handling**:
    * **Text**: Copy to clipboard, Show notification
    * **Image**: Save to gallery, Show notification
* **Global Prompt**: Set a persistent "User Prompt" that is applied to every single request.
* **Auto‑attach Selected Text**: If your template doesn’t include `$TEXT`, you can let the app auto‑attach the selected text at the top or bottom (Settings).
* **Language**: Choose the app language directly in Settings (default: English). Applies immediately.
* **Reliable Requests**: Built-in auto‑retry (up to 3 attempts) for transient errors and rate limits. The processing notification shows the current retry as “retry X/3”.

## 📖 How to Use

### 1. Process Selected Text (PROCESS_TEXT)

1.  Long-press to select text in any app (browser, messages, notes, etc.).
2.  In the context menu (⋮), tap **'Warp!'**.
3.  Search or pick a prompt from the floating panel that appears.

### 2. Process an Image (Share Sheet)

1.  From your Gallery, Photos, or any app, tap the 'Share' button on an image.
2.  Select **'CapyWarp'** from the system share sheet.
3.  Pick a prompt that is configured to accept image input.

Once the AI processing is complete, the result is delivered instantly according to that prompt's settings (notification, clipboard, etc.).

## 📝 Prompt Setup

Define prompts to automate your custom AI tasks by giving each a name and a template.

* **$TEXT Variable**: Use `$TEXT` in your template to insert the selected text.
    * 💡 `_Tip: If you omit $TEXT from your template, the selected text will be automatically appended **below** your template content._`
* **Output Type**: Choose whether to receive the AI's response as **'Text'** or **'Image'**.
* **Result Handling**:
    * **Text**: `Copy to clipboard`, `Show notification`
    * **Image**: `Show notification`, `Save to gallery`

## ⚙️ Settings

* **Gemini API Key**: [Required] Your API key to enable AI processing.
* **Gemini Model**: Select the model used for text-only prompts.
* **Image Model**: Select the model used when prompts include images (input or output).
* **User Prompt (Global)**: A persistent instruction merged into every request (e.g., "Always reply in casual English").
* **Theme**: System / Light / Dark
* **Backup & Restore**: Export or import your prompts and settings as a JSON file.

## 🔒 Permissions & Privacy

CapyWarp is designed with privacy as a priority.

* **Internet**: Used only to call the Gemini API with the key you provide.
* **Foreground Service**: Required to reliably complete AI requests in the background, as they can sometimes take longer.
* **Post Notifications**: Used to display AI results and error alerts. (Permission requested on Android 13+).
* **Media (Scoped)**: Used to save generated images to your device's gallery (Uses Android 10+ MediaStore API).
* **Local Storage**: Your API key and prompt configurations are stored securely and exclusively on your device.

## 🌍 Localization

The UI is internationalized into 10 languages. (Default: English)

* English (default)
* Korean (한국어)
* Japanese (日本語)
* Simplified Chinese (简体中文)
* Traditional Chinese (繁體中文)
* Spanish (Español)
* French (Français)
* German (Deutsch)
* Russian (Русский)
* Portuguese (Português)

## 💡 Notes & Troubleshooting

* **API Rate Limits**: On free API tiers, rate limits (429 errors) may apply. CapyWarp will automatically retry briefly when this occurs.
* **Image Delivery**: Generated images are delivered via notification. You can `Copy`, `Save to Gallery`, or `Share` the image directly from the notification. If the model returns text instead of an image, CapyWarp posts it as a text notification (no forced image decoding).

### 🔧 Troubleshooting

* **No Notifications (Android 13+)**: Please ensure that `Settings > Apps > CapyWarp` has the 'Notifications' permission enabled.
* **Image Generation Fails**: The app will retry a few times internally and attempts to parse data URIs automatically.
* **API Errors**: Ensure your Gemini API key is valid and that the model you selected (text-only vs. image-capable) matches the input/output type of your prompt.