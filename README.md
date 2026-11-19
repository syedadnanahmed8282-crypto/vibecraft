# Vibecraft (stage 3)

Stage 1: login, projects, chat on the phone, Supabase cloud backup.
Stage 2: real AI replies with your own API keys and automatic fallback between profiles.
Stage 3 (this build):
- Code blocks the AI sends are saved as project files, every change becomes a version (Code tab, History, Restore).
- Preview tab shows the design preview (preview.html) the AI adds.
- Publish tab commits the project (plus Gradle and workflow files) to your GitHub repository,
  watches the Actions build, and offers "Fix with AI" with the error lines if the build fails.

## Build this app's APK with GitHub Actions
1. Upload everything in this folder to a GitHub repository (keep the `.github` folder).
2. Actions tab > "Build APK" (also runs on every push to `main`).
3. Download the `vibecraft-debug-apk` artifact and install `app-debug.apk`.
4. If the build fails, download the `build-log` artifact and send it to Claude.

For publishing from the app you need a GitHub token that can write code and workflows to the target repository.
