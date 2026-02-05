# Contributing to OSNotes

First off, thank you for considering contributing to OSNotes! It's people like you that make open-source software thrive.

## 🌟 Ways to Contribute

### 🐛 Report Bugs
Found a bug? Help us fix it!

**Before submitting:**
- Check if the issue already exists
- Test on the latest version
- Gather reproduction steps

**Include in your report:**
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots/videos if applicable
- Crash logs if available

### 💡 Suggest Features
Have an idea? We'd love to hear it!

**Good feature requests include:**
- Clear use case and problem it solves
- How it fits with existing features
- Mockups or examples (if applicable)
- Why it matters to users

### 🔧 Submit Code
Ready to code? Awesome!

**Before you start:**
1. Check existing issues and PRs
2. Comment on the issue you want to work on
3. Fork the repository
4. Create a feature branch

**Code guidelines:**
- Follow Kotlin coding conventions
- Write clean, readable code
- Add comments for complex logic
- Include tests when applicable
- Update documentation if needed

### 📝 Improve Documentation
Documentation is just as important as code!

- Fix typos and grammar
- Clarify confusing sections
- Add examples and tutorials
- Translate to other languages

## 🚀 Development Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK with API 24-34
- Git

### Setup Steps
```bash
# 1. Fork and clone
git clone https://github.com/yourusername/osnotes.git
cd osnotes

# 2. Open in Android Studio
# File > Open > Select osnotes folder

# 3. Sync Gradle
# Android Studio will prompt you

# 4. Run the app
# Run > Run 'app' (or Shift+F10)
```

### Project Structure
```
osnotes/
├── app/src/main/java/com/osnotes/app/
│   ├── data/           # Data layer (database, repositories, storage)
│   ├── domain/         # Domain models and business logic
│   ├── ui/             # UI layer (screens, components, viewmodels)
│   │   ├── components/ # Reusable UI components
│   │   ├── screens/    # Full screen composables
│   │   ├── theme/      # Theme and styling
│   │   └── viewmodels/ # ViewModels for state management
│   └── di/             # Dependency injection modules
├── app/src/test/       # Unit tests
└── app/src/androidTest/# Instrumentation tests
```

## 📋 Pull Request Process

### 1. Create a Branch
```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/bug-description
```

### 2. Make Your Changes
- Write clean, documented code
- Follow existing code style
- Test your changes thoroughly
- Commit with clear messages

### 3. Commit Guidelines
```bash
# Good commit messages:
git commit -m "feat: add zoom level indicator"
git commit -m "fix: eraser cursor not visible on dark backgrounds"
git commit -m "docs: update installation instructions"

# Commit types:
# feat: New feature
# fix: Bug fix
# docs: Documentation changes
# style: Code style changes (formatting, etc)
# refactor: Code refactoring
# test: Adding or updating tests
# chore: Maintenance tasks
```

### 4. Push and Create PR
```bash
git push origin feature/your-feature-name
```

Then create a Pull Request on GitHub with:
- Clear title and description
- Link to related issue (if any)
- Screenshots/videos for UI changes
- Testing steps

### 5. Code Review
- Be patient and respectful
- Address feedback constructively
- Update your PR as needed
- Squash commits if requested

## 🧪 Testing

### Run Tests
```bash
# Unit tests
./gradlew test

# Instrumentation tests
./gradlew connectedAndroidTest
```

### Writing Tests
- Add unit tests for business logic
- Add UI tests for critical user flows
- Use descriptive test names
- Follow AAA pattern (Arrange, Act, Assert)

## 🎨 Code Style

### Kotlin Style Guide
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable names
- Keep functions small and focused
- Prefer immutability
- Use Kotlin idioms

### Compose Guidelines
- Keep composables small and reusable
- Hoist state when needed
- Use remember for expensive operations
- Follow Material Design 3 guidelines

### Example
```kotlin
// Good ✅
@Composable
fun NoteCard(
    note: NoteInfo,
    onNoteClick: (NoteInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onNoteClick(note) },
        modifier = modifier
    ) {
        Text(note.name)
    }
}

// Avoid ❌
@Composable
fun NoteCard(note: NoteInfo, onClick: () -> Unit) {
    // Missing modifier parameter
    // Unclear callback parameter name
}
```

## 🐛 Debugging Tips

### Enable Debug Logging
```kotlin
// Add to local.properties
debug.logging=true
```

### Common Issues
- **Build fails**: Clean and rebuild (`Build > Clean Project`)
- **Gradle sync issues**: Invalidate caches (`File > Invalidate Caches`)
- **Emulator slow**: Use physical device or enable hardware acceleration

## 📞 Getting Help

Stuck? Need guidance?

- 💬 [GitHub Discussions](https://github.com/yourusername/osnotes/discussions) - Ask questions
- 🐛 [GitHub Issues](https://github.com/yourusername/osnotes/issues) - Report bugs
- 📧 Email: your.email@example.com

## 🎯 Good First Issues

New to the project? Look for issues labeled:
- `good first issue` - Easy tasks for beginners
- `help wanted` - We need your help!
- `documentation` - Improve docs

## 📜 Code of Conduct

### Our Pledge
We are committed to providing a welcoming and inclusive environment for everyone.

### Expected Behavior
- Be respectful and considerate
- Accept constructive criticism gracefully
- Focus on what's best for the community
- Show empathy towards others

### Unacceptable Behavior
- Harassment or discrimination
- Trolling or insulting comments
- Personal or political attacks
- Publishing others' private information

### Enforcement
Violations may result in temporary or permanent ban from the project.

## 🙏 Thank You!

Every contribution, no matter how small, makes OSNotes better. Thank you for being part of this journey!

---

**Questions?** Open a [discussion](https://github.com/yourusername/osnotes/discussions) and we'll help you out!
