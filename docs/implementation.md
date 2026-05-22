# Nex V1 Implementation Roadmap

This document tracks the upcoming features and UI enhancements for the Nex V1 Personal AI Workspace.

## Immediate Tasks (1-2 Hour Sprint)

### 1. Functional Memory System
- **Persistence**: Replace dummy data in `MemoryActivity` with `SharedPreferences` or JSON storage.
- **Context Injection**: Pass "Pinned" memories into the AI system prompt so it "remembers" user preferences.

### 2. Prompt Engineering
- **Template Update**: Update `AIManager` to use the correct Qwen2.5/ChatML chat template to prevent rambling.

### 3. Quick Actions
- **Copy to Clipboard**: Add "Copy" functionality to the AI message bubbles or via a long-press menu.

## Current Planned Features

### 1. Message Actions
- [ ] **Copy to Clipboard**: Add a button to AI responses to quickly copy text/code.
- [ ] **Share Message**: Allow sharing specific AI responses to other apps.
- [ ] **Visual Integration**: Subtle icons appearing on hover or long-press.
- [ ] **Regenerate response**: Allowing model to regenerate the response. 

### 2. Markdown & Rich Text Support
- [ ] **Formatting**: Support for #Heading, **Bold**, *Italic*, and `Inline Code`.
- [ ] **Code Blocks**: Syntax highlighting or distinct backgrounds for code snippets.
- [ ] **Lists**: Proper rendering of bullet points and numbered lists.

### 3. Advanced Typing Animation
- [ ] **Pulsing Indicator**: Replace static "..." with a smooth, three-dot pulsing animation.
- [ ] **Dynamic State**: Better visual feedback while the AI model is processing.

### 4. Haptic Feedback
- [ ] **Tactile Response**: Add subtle vibrations (haptics) for:
    - Sending a message.
    - Receiving the first token of a response.
    - Interacting with menus and bottom sheets.

### 5. Persistence & History
- [ ] **Search**: Search through past conversations by keyword.
- [ ] **Export Chat**: Save conversations as PDF or Markdown files.

### 6. Settings & Customization
- [ ] **Model Selection**: Toggle between different AI model versions.
- [ ] **System Instructions**: Allow users to set a custom "Persona" or system prompt.

## Completed Enhancements
- [x] **Copy to Clipboard**: Added long-press action to all messages for quick copying.
- [x] **Functional Memory System**: Memories are now persistent and injected into AI context.
- [x] **Prompt Template**: Qwen2.5 (ChatML) chat template implemented to prevent rambling.
- [x] **Live Updates**: Show the response stream as the model generates tokens.
- [x] **Auto-Scroll**: Automatically scroll to the bottom during message generation.
- [x] **Recent Chat Management**: Rename and Delete options via long-press and three-dot menu.
- [x] **Theme Refinement**: Standardized monochrome selection colors and cursors.
- [x] **Custom Bottom Sheets**: Modern, rounded-corner sheets for options and renaming.
- [x] **Basic Chat UI**: Core messaging interface with user and AI message bubbles.
- [x] **Navigation Drawer**: Side menu for switching between different chat sessions.
